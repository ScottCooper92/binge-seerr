package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.HTTP_NOT_FOUND
import io.github.scottcooper92.binge.seerr.seerr.SeerrLibraryDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrLibraryEnabledBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrScanCommandBody
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject

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
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : EditorViewModel<MediaServerForm>(dispatcher) {
        /** Test seam for the scan's poll; the constructor is Hilt's. */
        internal var scanPollMillis: Long = SCAN_POLL_MILLIS

        private val extrasState = MutableStateFlow(MediaServerExtras())
        val extras: StateFlow<MediaServerExtras> = extrasState.asStateFlow()

        private var kind = MediaServerKind.Plex
        private var scanPoll: Job? = null

        /**
         * Serializes the released-server fallback's library writes. That path sends the whole
         * enabled set computed from local state, so two of them racing each other would each
         * compute from a snapshot that is missing the other's change and the one that lands
         * last on the server would silently drop it; this makes the second wait for the first
         * to finish and fold into local state before it reads that state.
         *
         * A toggle and a sync are both whole-set writes on that path, so both take it.
         */
        private val libraryWriteMutex = Mutex()

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
            viewModelScope.launch(dispatcher) {
                val result =
                    runCatching {
                        val api = connection.api()
                        orOnNotFound(
                            newer = {
                                api.setLibraryEnabled(kind.apiSegment, id, SeerrLibraryEnabledBody(enabled)).let { updated ->
                                    replace(updated)
                                }
                            },
                            released = {
                                libraryWriteMutex.withLock {
                                    val enabledIds =
                                        extrasState.value.libraries
                                            .filter { if (it.id == id) enabled else it.enabled }
                                            .map { it.id }
                                    val libraries = api.mediaLibraries(kind.apiSegment, enable = enabledIds.joinToString(","))
                                    // Folded into local state before the lock is released, so the next
                                    // waiting toggle computes its enabled set from this one's result
                                    // rather than the snapshot from before it landed.
                                    extrasState.update {
                                        it.copy(
                                            libraries = libraries.map { dto -> dto.toLibrary() },
                                            busyLibraryIds = it.busyLibraryIds - id,
                                        )
                                    }
                                    libraries
                                }
                            },
                        )
                    }
                result.onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
                // Libraries and busyLibraryIds must land in the same emission: a collector observing
                // libraries updated but the id still busy (or vice versa) is an inconsistent state.
                extrasState.update {
                    it.copy(
                        libraries = result.getOrNull()?.map { dto -> dto.toLibrary() } ?: it.libraries,
                        busyLibraryIds = it.busyLibraryIds - id,
                    )
                }
            }
        }

        /**
         * Re-reads the libraries from the media server, so a newly added one appears with its toggle.
         *
         * The released route rewrites the whole enabled set on every call, outside its `sync` branch
         * and unguarded, so an absent `enable` is an instruction to disable everything and it is
         * saved. The fallback therefore sends the currently enabled ids alongside `sync`, as
         * Overseerr's own web client does, and reads them under the same lock a toggle takes.
         */
        fun syncLibraries() {
            if (extrasState.value.syncingLibraries) return
            extrasState.update { it.copy(syncingLibraries = true) }
            viewModelScope.launch(dispatcher) {
                runCatching {
                    val api = connection.api()
                    orOnNotFound(
                        newer = { api.syncLibraries(kind.apiSegment) },
                        released = {
                            libraryWriteMutex.withLock {
                                val enabledIds =
                                    extrasState.value.libraries
                                        .filter { it.enabled }
                                        .map { it.id }
                                // Nothing enabled needs no parameter: the route's own answer to an absent
                                // enable is then the correct one.
                                api
                                    .mediaLibraries(
                                        kind.apiSegment,
                                        enable = enabledIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                                        sync = true,
                                    ).also { setLibraries(it) }
                            }
                        },
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
            viewModelScope.launch(dispatcher) {
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
                viewModelScope.launch(dispatcher) {
                    var running = true
                    while (running) {
                        delay(scanPollMillis)
                        // A failed poll is not an answer: the last state stands and the next tick asks again.
                        val latest = runCatching { connection.api().scanStatus(kind.apiSegment).toScan() }.getOrNull()
                        if (latest != null) {
                            extrasState.update { it.copy(scan = latest) }
                            running = latest.running
                        }
                    }
                }
        }

        fun openServerPicker() {
            extrasState.update { it.copy(picker = PlexServerPicker.Loading) }
            viewModelScope.launch(dispatcher) {
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
                    SeerrLibraryDto(library.id, library.name, library.enabled, library.type.toSeerrType(), library.lastScanMillis)
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
