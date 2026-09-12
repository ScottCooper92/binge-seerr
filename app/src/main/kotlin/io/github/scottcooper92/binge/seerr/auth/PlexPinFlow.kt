package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.PlexClientIdentity
import io.github.scottcooper92.binge.seerr.seerr.PlexPinDto
import io.github.scottcooper92.binge.seerr.seerr.PlexTvApi
import io.github.scottcooper92.binge.seerr.seerr.plexTvApi
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val HTTP_NOT_FOUND = 404

/** The user did not approve the PIN before plex.tv expired it. */
class PlexPinExpiredException : IllegalStateException("The Plex PIN expired before it was approved")

/** A minted PIN: what to show, where to send the user, and what to poll with. */
data class PlexPin(
    val id: Long,
    val code: String,
    val authUrl: String,
    val expiresAt: Instant?,
    val identity: PlexClientIdentity,
)

/**
 * The plex.tv side of a Plex sign-in. [start] mints a PIN and [awaitToken] polls it until the
 * user approves it in the browser, or plex.tv expires it. The token then goes to Seerr's
 * `auth/plex` through [SeerrConnection.logInWithPlex]; nothing here talks to the Seerr server.
 * The identity is resolved per flow because its identifier is minted on first use.
 */
class PlexPinFlow(
    private val identity: suspend () -> PlexClientIdentity,
    private val apis: (PlexClientIdentity) -> PlexTvApi = { plexTvApi(it) },
    private val pollInterval: Duration = 2.seconds,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun start(): PlexPin {
        val identity = identity()
        return apis(identity).createPin().toPin(identity)
    }

    suspend fun awaitToken(pin: PlexPin): String {
        val api = apis(pin.identity)
        while (true) {
            if (pin.expiresAt?.let { it <= now() } == true) throw PlexPinExpiredException()
            val polled =
                try {
                    api.pin(pin.id)
                } catch (e: HttpException) {
                    if (e.code() == HTTP_NOT_FOUND) throw PlexPinExpiredException() else throw e
                }
            polled.authToken?.takeIf { it.isNotBlank() }?.let { return it }
            delay(pollInterval)
        }
    }

    private fun PlexPinDto.toPin(identity: PlexClientIdentity): PlexPin =
        PlexPin(
            id = id,
            code = code,
            authUrl = identity.authUrl(code),
            expiresAt = expiresAt?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() },
            identity = identity,
        )
}
