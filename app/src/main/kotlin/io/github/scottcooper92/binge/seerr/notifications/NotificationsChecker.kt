package io.github.scottcooper92.binge.seerr.notifications

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One poll's outcome. A transient failure is retried; the server rejecting the saved credentials
 * is not, since no retry can succeed: the user is asked to sign in again and the poll paused.
 */
enum class CheckResult { Ok, TransientFailure, AuthFailure }

/**
 * The poll itself, pure enough to test against a scripted server: each signal the user turned
 * on, and the server lets them see, is diffed against its saved state and anything fresh is
 * handed to the notifier. A failed fetch leaves that signal's state where it was, so the next
 * run catches up. Nothing runs while not connected or while nothing could be shown.
 */
class NotificationsChecker
    @Inject
    constructor(
        private val prefs: NotificationPrefs,
        private val connection: SeerrConnection,
        private val feeds: NotificationFeeds,
        private val notifier: SeerrNotifier,
    ) {
        suspend fun check(): CheckResult {
            if (!prefs.anyEnabled.first()) return CheckResult.Ok
            if (connection.credentials.first() == null) return CheckResult.Ok
            if (!notifier.canPost()) return CheckResult.Ok
            val viewer = attempt { connection.authenticatedUser() }.getOrElse { return it.toResult() }
            val permissions = viewer.toPermissions()
            val hasIssues = attempt { connection.profile().hasIssues }.getOrDefault(false)
            val requests =
                if (permissions.canManageRequests && prefs.isEnabled(NotificationSignal.PendingRequests)) {
                    checkFeed(NotificationSignal.PendingRequests, { feeds.pendingRequests(it) }, { it.id }, notifier::notifyNewRequests)
                } else {
                    CheckResult.Ok
                }
            val issues =
                if (permissions.canManageIssues && hasIssues && prefs.isEnabled(NotificationSignal.OpenIssues)) {
                    checkFeed(NotificationSignal.OpenIssues, { feeds.openIssues(it) }, { it.id }, notifier::notifyNewIssues)
                } else {
                    CheckResult.Ok
                }
            return worstOf(worstOf(requests, issues), checkOwnRequests())
        }

        /**
         * A feed: fetch what is newer than the cursor, announce it, advance. A seed run stores the
         * newest id without announcing; an empty feed seeds to 0, a real cursor, so the first row
         * to arrive is announced rather than seeding again.
         */
        private suspend fun <T> checkFeed(
            signal: NotificationSignal,
            fetch: suspend (sinceId: Int?) -> List<T>,
            idOf: (T) -> Int,
            notify: (List<T>) -> Unit,
        ): CheckResult {
            val cursor = prefs.cursor(signal)
            val fresh = attempt { fetch(cursor) }.getOrElse { return it.toResult() }
            if (cursor == null) {
                prefs.setCursor(signal, fresh.maxOfOrNull(idOf) ?: 0)
                return CheckResult.Ok
            }
            if (fresh.isNotEmpty()) {
                notify(fresh)
                prefs.setCursor(signal, fresh.maxOf(idOf))
            }
            return CheckResult.Ok
        }

        /** One read of the user's own requests, then each state signal that is on. */
        private suspend fun checkOwnRequests(): CheckResult {
            val signals =
                listOf(NotificationSignal.RequestAvailable, NotificationSignal.RequestApproved, NotificationSignal.RequestDeclined)
            val on = signals.filter { prefs.isEnabled(it) }
            if (on.isEmpty()) return CheckResult.Ok
            val requests = attempt { feeds.ownRequests() }.getOrElse { return it.toResult() }
            on.forEach { signal ->
                checkTransition(signal, requests.filter { it.isIn(signal) }) { items ->
                    when (signal) {
                        NotificationSignal.RequestAvailable -> notifier.notifyRequestsAvailable(items)
                        NotificationSignal.RequestApproved -> notifier.notifyRequestsApproved(items)
                        else -> notifier.notifyRequestsDeclined(items)
                    }
                }
            }
            return CheckResult.Ok
        }

        /**
         * Announces the requests in the state whose id is not in the saved set, then saves the
         * current set. Saving the current set prunes itself: a request that leaves the state drops
         * out, so re-entering it is announced again. A null set is a seed run and announces nothing.
         */
        private suspend fun checkTransition(
            signal: NotificationSignal,
            current: List<SeerrRequestDto>,
            notify: suspend (List<RequestItem>) -> Unit,
        ) {
            val currentIds = current.mapTo(mutableSetOf()) { it.id }
            val seen = prefs.notifiedIds(signal)
            if (seen == null) {
                prefs.setNotifiedIds(signal, currentIds)
                return
            }
            val fresh = current.filter { it.id !in seen }
            if (fresh.isNotEmpty()) {
                val titled = attempt { feeds.titled(fresh) }.getOrElse { return }
                if (titled.isNotEmpty()) notify(titled)
            }
            prefs.setNotifiedIds(signal, currentIds)
        }
    }

private fun SeerrRequestDto.isIn(signal: NotificationSignal): Boolean =
    when (signal) {
        NotificationSignal.RequestAvailable -> media.status == SeerrMediaStatusCode.Available
        NotificationSignal.RequestApproved -> status == SeerrRequestStatusCode.Approved
        NotificationSignal.RequestDeclined -> status == SeerrRequestStatusCode.Declined
        NotificationSignal.PendingRequests, NotificationSignal.OpenIssues -> false
    }

private fun Throwable.toResult(): CheckResult =
    if (toSeerrError() == SeerrError.Unauthorized) CheckResult.AuthFailure else CheckResult.TransientFailure

private fun worstOf(
    a: CheckResult,
    b: CheckResult,
): CheckResult = if (a.ordinal >= b.ordinal) a else b

/** [runCatching] would swallow the worker's cancellation; this lets it through. */
private inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Result.failure(e)
    }
