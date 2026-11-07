package com.cooper.binge.companion.seerr.ui.requests

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import com.cooper.binge.companion.seerr.seerr.toPermissions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Whether an empty download queue means nothing is downloading. Seerr fills the queue only from a Radarr or
 * Sonarr instance with Enable Scan on, so with it off an empty queue says nothing (#1733). Only an
 * administrator can read that setting, so the answer is "unknown" for anyone else and wherever a read failed.
 *
 * A list of requests cannot say which instance a version lives on, so it sees a type only when every instance of
 * it scans. A page that holds the media can name the instance, and asks [sees] with it: a mixed setup then reads
 * right for the version on the scanning instance. An instance the settings do not list reads neutral, never wrong.
 */
data class DownloadQueueGate(
    val movies: Boolean = false,
    val tv: Boolean = false,
    val scanning: Set<ScanningInstance> = emptySet(),
) {
    /** One Radarr or Sonarr instance with Enable Scan on. */
    data class ScanningInstance(
        val type: RequestMediaType,
        val is4k: Boolean,
        val id: Int,
    )

    fun sees(type: RequestMediaType): Boolean =
        when (type) {
            RequestMediaType.Movie -> movies
            RequestMediaType.Tv -> tv
        }

    /** For a media type string off a DTO; a type this app does not render is never seen. */
    fun sees(mediaType: String): Boolean = mediaType.toRequestMediaTypeOrNull()?.let(::sees) == true

    /**
     * For the [is4k] version of [media]: its own instance's scan where Seerr names the instance, else the type's
     * answer as a list gives it.
     */
    fun sees(
        media: SeerrRequestMediaDto,
        is4k: Boolean,
    ): Boolean {
        val type = media.mediaType.toRequestMediaTypeOrNull() ?: return false
        val id = media.serviceIdFor(is4k) ?: return sees(type)
        return ScanningInstance(type, is4k, id) in scanning
    }

    companion object {
        val Unknown = DownloadQueueGate()

        fun from(
            radarr: List<SeerrServiceSettingsDto>,
            sonarr: List<SeerrServiceSettingsDto>,
        ) = DownloadQueueGate(
            movies = radarr.isNotEmpty() && radarr.all { it.syncEnabled },
            tv = sonarr.isNotEmpty() && sonarr.all { it.syncEnabled },
            scanning =
                radarr.scanning(RequestMediaType.Movie) + sonarr.scanning(RequestMediaType.Tv),
        )

        private fun List<SeerrServiceSettingsDto>.scanning(type: RequestMediaType): Set<ScanningInstance> =
            mapNotNull { dto -> dto.id?.takeIf { dto.syncEnabled }?.let { ScanningInstance(type, dto.is4k, it) } }.toSet()
    }
}

/**
 * The [DownloadQueueGate] for the connected server, read from its services settings and kept for a minute so a
 * list and a detail page opened together ask once. A read that failed is kept for a few seconds as "unknown", so a
 * list of rows, each asking in turn, is one failed read and not one per row. A different server is never answered
 * from what was kept for another.
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
            val maxAge: Duration,
        )

        @Volatile
        private var kept: Kept? = null

        suspend fun current(): DownloadQueueGate {
            val admin = attempt { connection.recentAuthenticatedUser() }.getOrNull().toPermissions().isAdmin
            if (!admin) return DownloadQueueGate.Unknown
            val server = attempt { connection.current().baseUrl }.getOrNull() ?: return DownloadQueueGate.Unknown
            kept?.takeIf { it.server == server && clock() - it.at < it.maxAge.inWholeMilliseconds }?.let { return it.gate }
            val read =
                attempt {
                    val api = connection.api()
                    coroutineScope {
                        val radarr = async { api.radarrSettings() }
                        val sonarr = async { api.sonarrSettings() }
                        DownloadQueueGate.from(radarr.await(), sonarr.await())
                    }
                }
            val gate = read.getOrDefault(DownloadQueueGate.Unknown)
            kept = Kept(server, clock(), gate, if (read.isSuccess) MAX_AGE else FAILURE_MAX_AGE)
            return gate
        }

        private companion object {
            val MAX_AGE: Duration = 1.minutes
            val FAILURE_MAX_AGE: Duration = 15.seconds
        }
    }

/** [this] with its empty queue read against the gate: one to trust where the server's scan is on. */
suspend fun RequestItem.withQueueFrom(gates: DownloadQueueGates): RequestItem = copy(queueTracked = gates.current().sees(mediaType))
