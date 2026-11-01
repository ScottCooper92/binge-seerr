package com.cooper.binge.companion.seerr.ui.requests

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import com.cooper.binge.companion.seerr.seerr.toPermissions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Whether an empty download queue means nothing is downloading. Seerr fills the queue only from a Radarr or
 * Sonarr instance with Enable Scan on, so with it off an empty queue says nothing (#1733). Only an
 * administrator can read that setting, so the answer is "unknown" for anyone else and wherever a read failed.
 *
 * A type is seen only when every instance of it scans, because the media a screen holds does not say which
 * instance a version lives on. A mixed setup reads neutral, never wrong.
 */
data class DownloadQueueGate(
    val movies: Boolean = false,
    val tv: Boolean = false,
) {
    fun sees(type: RequestMediaType): Boolean =
        when (type) {
            RequestMediaType.Movie -> movies
            RequestMediaType.Tv -> tv
        }

    /** For a media type string off a DTO; a type this app does not render is never seen. */
    fun sees(mediaType: String): Boolean = mediaType.toRequestMediaTypeOrNull()?.let(::sees) == true

    companion object {
        val Unknown = DownloadQueueGate()

        fun from(
            radarr: List<SeerrServiceSettingsDto>,
            sonarr: List<SeerrServiceSettingsDto>,
        ) = DownloadQueueGate(
            movies = radarr.isNotEmpty() && radarr.all { it.syncEnabled },
            tv = sonarr.isNotEmpty() && sonarr.all { it.syncEnabled },
        )
    }
}

/**
 * The [DownloadQueueGate] for the connected server, read from its services settings and kept for a minute so a
 * list and a detail page opened together ask once. A different server, or a read that failed, is not kept.
 */
@Singleton
class DownloadQueueGates
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) {
        /** What the cache ages against; a test pins it. */
        internal var clock: () -> Long = System::currentTimeMillis

        private class Kept(
            val server: String,
            val at: Long,
            val gate: DownloadQueueGate,
        )

        @Volatile
        private var kept: Kept? = null

        suspend fun current(): DownloadQueueGate {
            val admin = attempt { connection.recentAuthenticatedUser() }.getOrNull().toPermissions().isAdmin
            if (!admin) return DownloadQueueGate.Unknown
            val server = attempt { connection.current().baseUrl }.getOrNull() ?: return DownloadQueueGate.Unknown
            kept?.takeIf { it.server == server && clock() - it.at < MAX_AGE.inWholeMilliseconds }?.let { return it.gate }
            val read =
                attempt {
                    val api = connection.api()
                    coroutineScope {
                        val radarr = async { api.radarrSettings() }
                        val sonarr = async { api.sonarrSettings() }
                        DownloadQueueGate.from(radarr.await(), sonarr.await())
                    }
                }
            return read
                .onSuccess { kept = Kept(server, clock(), it) }
                .getOrDefault(DownloadQueueGate.Unknown)
        }

        private companion object {
            val MAX_AGE: Duration = 1.minutes
        }
    }

/** [this] with its empty queue read against the gate: one to trust where the server's scan is on. */
suspend fun RequestItem.withQueueFrom(gates: DownloadQueueGates): RequestItem = copy(queueTracked = gates.current().sees(mediaType))
