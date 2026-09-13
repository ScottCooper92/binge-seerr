package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrLibraryDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrLibraryEnabledBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrScanCommandBody
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

private const val HTTP_NOT_FOUND = 404

/**
 * The media-server page: the connection as an editor over `settings/plex` or `settings/jellyfin`
 * (which the server's kind decides), with the libraries, the full scan and the Plex server picker
 * beside it in [extras]. The two library writes exist in two shapes — `develop` grew a per-library
 * `PUT` and a `library/sync`, a released server still takes `enable=` and `sync=` on the `GET` —
 * so each tries the newer one first and falls back on a 404, which is how a released server says
 * the route is not there.
 */
@HiltViewModel
class MediaServerViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : EditorViewModel<MediaServerForm>() {
        /** Test seam for the scan's poll; the constructor is Hilt's. */
        internal var scanPollMillis: Long = SCAN_POLL_MILLIS

        private val extrasState = MutableStateFlow(MediaServerExtras())
        val extras: StateFlow<MediaServerExtras> = extrasState.asStateFlow()

        private var kind = MediaServerKind.Plex
        private var scanPoll: Job? = null

        init {
            reload()
        }

        override suspend fun load(): MediaServerForm {
            val api = connection.api()
            kind = connection.profile().mediaServer.toKind()
            val form =
                when (kind) {
                    MediaServerKind.Plex -> api.plexSettings().let { it.toForm().also { _ -> setLibraries(it.libraries) } }
                    else -> api.jellyfinSettings().let { it.toForm(kind).also { _ -> setLibraries(it.libraries) } }
                }
            runCatching { api.scanStatus(kind.apiSegment) }.getOrNull()?.let { applyScan(it.toScan()) }
            return form
        }

        override suspend fun write(draft: MediaServerForm): MediaServerForm {
            val api = connection.api()
            return when (kind) {
                MediaServerKind.Plex ->
                    api.updatePlexSettings(draft.toPlexBody()).let {
                        it.toForm().also { _ ->
                            setLibraries(it.libraries)
                        }
                    }
                else -> api.updateJellyfinSettings(draft.toJellyfinBody()).let { it.toForm(kind).also { _ -> setLibraries(it.libraries) } }
            }
        }

        override fun canSave(draft: MediaServerForm): Boolean = draft.valid

        /** Turns one library on or off: the per-library route where the server has it, else the whole enabled set on the `GET`. */
        fun setLibraryEnabled(
            id: String,
            enabled: Boolean,
        ) {
            if (id in extrasState.value.busyLibraryIds) return
            extrasState.update { it.copy(busyLibraryIds = it.busyLibraryIds + id) }
            viewModelScope.launch {
                runCatching {
                    val api = connection.api()
                    orOnNotFound(
                        newer = {
                            api.setLibraryEnabled(kind.apiSegment, id, SeerrLibraryEnabledBody(enabled)).let { updated ->
                                replace(updated)
                            }
                        },
                        released = {
                            val enabledIds =
                                extrasState.value.libraries
                                    .filter { if (it.id == id) enabled else it.enabled }
                                    .map { it.id }
                            api.mediaLibraries(kind.apiSegment, enable = enabledIds.joinToString(","))
                        },
                    )
                }.onSuccess { libraries -> setLibraries(libraries) }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
                extrasState.update { it.copy(busyLibraryIds = it.busyLibraryIds - id) }
            }
        }

        /** Re-reads the libraries from the media server, so a newly added one appears with its toggle. */
        fun syncLibraries() {
            if (extrasState.value.syncingLibraries) return
            extrasState.update { it.copy(syncingLibraries = true) }
            viewModelScope.launch {
                runCatching {
                    val api = connection.api()
                    orOnNotFound(
                        newer = { api.syncLibraries(kind.apiSegment) },
                        released = { api.mediaLibraries(kind.apiSegment, sync = true) },
                    )
                }.onSuccess { libraries ->
                    setLibraries(libraries)
                    notify(EditorEvent.Notice(R.string.server_settings_libraries_synced))
                }.onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
                extrasState.update { it.copy(syncingLibraries = false) }
            }
        }

        fun startScan() = command(SeerrScanCommandBody(start = true))

        fun cancelScan() = command(SeerrScanCommandBody(cancel = true))

        private fun command(body: SeerrScanCommandBody) {
            viewModelScope.launch {
                runCatching { connection.api().scan(kind.apiSegment, body).toScan() }
                    .onSuccess { applyScan(it) }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        /** A running scan is followed until it stops; the poll lives with the page and ends with it. */
        private fun applyScan(scan: LibraryScan) {
            extrasState.update { it.copy(scan = scan) }
            if (!scan.running) {
                scanPoll?.cancel()
                scanPoll = null
                return
            }
            if (scanPoll?.isActive == true) return
            scanPoll =
                viewModelScope.launch {
                    while (true) {
                        delay(scanPollMillis)
                        val latest = runCatching { connection.api().scanStatus(kind.apiSegment).toScan() }.getOrNull() ?: continue
                        extrasState.update { it.copy(scan = latest) }
                        if (!latest.running) break
                    }
                }
        }

        fun openServerPicker() {
            extrasState.update { it.copy(picker = PlexServerPicker.Loading) }
            viewModelScope.launch {
                val picker =
                    runCatching { connection.api().plexServers().toChoices() }
                        .fold(onSuccess = { PlexServerPicker.Ready(it) }, onFailure = { PlexServerPicker.Failed(it.toSeerrError()) })
                extrasState.update { current -> if (current.picker == null) current else current.copy(picker = picker) }
            }
        }

        fun closeServerPicker() = extrasState.update { it.copy(picker = null) }

        /** Fills the address from a picked connection; the name follows once the server reaches it on save. */
        fun chooseConnection(
            server: PlexServerChoice,
            choice: PlexConnection,
        ) {
            edit { it.copy(serverName = server.name, host = choice.address, port = choice.port.toString(), useSsl = choice.useSsl) }
            closeServerPicker()
        }

        private fun setLibraries(libraries: List<SeerrLibraryDto>) =
            extrasState.update {
                it.copy(libraries = libraries.map { dto -> dto.toLibrary() })
            }

        private fun replace(updated: SeerrLibraryDto): List<SeerrLibraryDto> =
            extrasState.value.libraries.map { library ->
                if (library.id ==
                    updated.id
                ) {
                    updated
                } else {
                    SeerrLibraryDto(library.id, library.name, library.enabled, null, library.lastScanMillis)
                }
            }

        private suspend fun <T> orOnNotFound(
            newer: suspend () -> T,
            released: suspend () -> T,
        ): T =
            try {
                newer()
            } catch (e: HttpException) {
                if (e.code() == HTTP_NOT_FOUND) released() else throw e
            }

        private companion object {
            const val SCAN_POLL_MILLIS = 2_000L
        }
    }

/** For [MediaServerViewModel.load]'s two branches to share one call shape. */
private inline fun <T> SeerrApi.let(block: (SeerrApi) -> T): T = block(this)
