package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.SavedStateHandle
import io.github.scottcooper92.binge.seerr.auth.PlexPin
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrQuickConnect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant

private const val PENDING_LINK = "pendingLink"

/**
 * The sign-ins that finish in another app: Plex in the browser, Quick Connect on Jellyfin. It owns
 * the wait and the [PendingLink] that outlives the process while the user is over there — [start]
 * begins one, [resume] picks up the one a process that has since died left behind, and either ends
 * in [onFinished] with what went wrong or nothing.
 */
internal class SetupLinks(
    private val scope: CoroutineScope,
    private val connection: SeerrConnection,
    private val plex: PlexPinFlow,
    private val savedState: SavedStateHandle,
    private val onLink: (LinkFlow) -> Unit,
    private val onFinished: (Throwable?) -> Unit,
) {
    private var job: Job? = null

    /** The link left waiting when the process died, if there was one. */
    fun pending(): PendingLink? =
        savedState.get<String>(PENDING_LINK)?.let { stored -> runCatching { Json.decodeFromString<PendingLink>(stored) }.getOrNull() }

    fun startPlex(
        server: SetupServer,
        editing: Boolean,
    ) {
        job =
            scope.launch {
                val pin = attempt { plex.start() }.getOrElse { failure -> return@launch finished(failure) }
                keep(
                    PendingLink.Plex(
                        serverUrl = server.baseUrl,
                        code = pin.code,
                        editing = editing,
                        pinId = pin.id,
                        expiresAtEpochMillis = pin.expiresAt?.toEpochMilli(),
                    ),
                )
                onLink(LinkFlow.Plex(pin.code, pin.authUrl, launchPending = true))
                awaitPlex(server, pin)
            }
    }

    fun startQuickConnect(
        server: SetupServer,
        editing: Boolean,
    ) {
        job =
            scope.launch {
                val session = connection.startQuickConnect(server.baseUrl).getOrElse { failure -> return@launch finished(failure) }
                keep(
                    PendingLink.QuickConnect(
                        serverUrl = server.baseUrl,
                        code = session.code,
                        editing = editing,
                        secret = session.secret,
                    ),
                )
                onLink(LinkFlow.QuickConnect(session.code))
                awaitQuickConnect(server, session)
            }
    }

    /** Picks the wait back up, on the server the caller has just read again. */
    fun resume(
        server: SetupServer,
        pending: PendingLink,
    ) {
        job =
            scope.launch {
                when (pending) {
                    is PendingLink.Plex -> resumePlex(server, pending)
                    is PendingLink.QuickConnect -> {
                        onLink(LinkFlow.QuickConnect(pending.code))
                        awaitQuickConnect(server, SeerrQuickConnect(code = pending.code, secret = pending.secret))
                    }
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        forget()
    }

    fun forget() {
        savedState.remove<String>(PENDING_LINK)
    }

    private suspend fun resumePlex(
        server: SetupServer,
        pending: PendingLink.Plex,
    ) {
        val expiresAt = pending.expiresAtEpochMillis?.let(Instant::ofEpochMilli)
        val pin = attempt { plex.resume(pending.pinId, pending.code, expiresAt) }.getOrElse { failure -> return finished(failure) }
        // The user has just come back to the app, so the approval page is theirs to reopen from the
        // sheet rather than something to throw them straight back out to.
        onLink(LinkFlow.Plex(pin.code, pin.authUrl, launchPending = false))
        awaitPlex(server, pin)
    }

    private suspend fun awaitPlex(
        server: SetupServer,
        pin: PlexPin,
    ) = finished(attempt { connection.logInWithPlex(server.baseUrl, plex.awaitToken(pin)).getOrThrow() }.exceptionOrNull())

    private suspend fun awaitQuickConnect(
        server: SetupServer,
        session: SeerrQuickConnect,
    ) = finished(connection.finishQuickConnect(server.baseUrl, session).exceptionOrNull())

    /**
     * A cancelled wait is the one ending that keeps the pending link: the process dying with the
     * user still approving is exactly what it is stored for.
     */
    private fun finished(failure: Throwable?) {
        if (failure is CancellationException) return
        forget()
        onFinished(failure)
    }

    private fun keep(pending: PendingLink) {
        savedState[PENDING_LINK] = Json.encodeToString<PendingLink>(pending)
    }
}

/** [runCatching] would swallow the cancellation [SetupLinks.cancel] sends; this lets it back out. */
private inline fun <T> attempt(block: () -> T): Result<T> =
    runCatching(block).onFailure { failure -> if (failure is CancellationException) throw failure }
