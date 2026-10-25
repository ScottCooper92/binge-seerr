package com.cooper.binge.companion.seerr.service

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.QualityLabels
import com.cooper.binge.companion.seerr.seerr.SeerrDestination
import com.cooper.binge.companion.seerr.seerr.arrServer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Profiles change rarely, but the process outlives a bind, so a name is not trusted forever. */
private const val PROFILES_MAX_AGE_MILLIS = 3 * 60 * 60 * 1000L

/** A server that could not be read is asked again soon: an *arr that is down comes back, and a refused read costs one call. */
private const val MISS_MAX_AGE_MILLIS = 5 * 60 * 1000L

/**
 * Names each request's quality profile from the *arr's own profile list, which the request itself
 * carries only as an id.
 *
 * `GET service/{radarr,sonarr}/{id}` needs a signed-in user and nothing more, but it asks the *arr
 * live, so it fails when that is unreachable. Any failure reads as no label and is remembered for
 * [MISS_MAX_AGE_MILLIS], never raised: a missing label must not fail the status or the list it
 * decorates. One read serves every row for that server, held for [PROFILES_MAX_AGE_MILLIS].
 */
internal class QualityProfileLabels(
    private val connection: SeerrConnection,
    private val clock: () -> Long,
) {
    private val lock = Mutex()
    private val cache = HashMap<ServerKey, Profiles>()

    /** The lookup for these destinations, with every server they need already read, so the mapping stays plain code. */
    suspend fun forDestinations(destinations: Collection<SeerrDestination>): QualityLabels {
        val wanted = destinations.filter { it.isResolvable }.mapNotNull { it.serverId?.let { id -> ServerKey(it.isTv, id) } }.distinct()
        if (wanted.isEmpty()) return QualityLabels.None
        val baseUrl = connection.current().baseUrl
        val names = wanted.associateWith { profileNames(baseUrl, it) }
        return QualityLabels { destination ->
            val key = destination.serverId?.let { ServerKey(destination.isTv, it) }
            names[key]?.get(destination.profileId)
        }
    }

    private suspend fun profileNames(
        baseUrl: String,
        server: ServerKey,
    ): Map<Int, String> =
        lock.withLock {
            val key = server.copy(baseUrl = baseUrl)
            val now = clock()
            cache[key]?.takeIf { now - it.readAtMillis in 0 until it.maxAgeMillis }?.let { return it.names }
            val read = readProfiles(server)
            val profiles = if (read != null) Profiles(read, now, PROFILES_MAX_AGE_MILLIS) else Profiles(emptyMap(), now, MISS_MAX_AGE_MILLIS)
            cache[key] = profiles
            profiles.names
        }

    private suspend fun readProfiles(server: ServerKey): Map<Int, String>? =
        try {
            connection.api().arrServer(server.isTv, server.serverId).profiles.associate { it.id to it.name }
        } catch (_: HttpException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }

    private data class ServerKey(
        val isTv: Boolean,
        val serverId: Int,
        val baseUrl: String = "",
    )

    private class Profiles(
        val names: Map<Int, String>,
        val readAtMillis: Long,
        val maxAgeMillis: Long,
    )
}
