package com.cooper.binge.companion.seerr.notifications

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.toPermissions
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One poll's outcome. A transient failure is retried at the next interval; the server rejecting the saved credentials
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
        private val cursorFeeds: CursorFeeds,
    ) {
        suspend fun check(): CheckResult {
            if (!prefs.anyEnabled.first()) return CheckResult.Ok
            if (connection.credentials.first() == null) return CheckResult.Ok
            if (!notifier.canPost()) return CheckResult.Ok
            val viewer = attempt { connection.authenticatedUser() }.getOrElse { return it.toResult() }
            val permissions = viewer.toPermissions()
            val userId = viewer.id
            val hasIssues = attempt { connection.profile().hasIssues }.getOrDefault(false)
            val requests =
                if (permissions.canManageRequests && prefs.isEnabled(NotificationSignal.PendingRequests)) {
                    cursorFeeds.check(
                        NotificationSignal.PendingRequests,
                        userId,
                        feeds::seedPendingRequests,
                        feeds::pendingRequests,
                        notifier::notifyNewRequests,
                    )
                } else {
                    CheckResult.Ok
                }
            val issues =
                if (permissions.canManageIssues && hasIssues && prefs.isEnabled(NotificationSignal.OpenIssues)) {
                    cursorFeeds.check(
                        NotificationSignal.OpenIssues,
                        userId,
                        feeds::seedOpenIssues,
                        feeds::openIssues,
                        notifier::notifyNewIssues,
                    )
                } else {
                    CheckResult.Ok
                }
            return worstOf(worstOf(requests, issues), checkOwnRequests())
        }

        /**
         * One read of the user's own requests, then each state signal that is on.
         *
         * The read is told which ids are still waiting on an outcome, so it walks far enough to find
         * every one of them however many requests the user has made since (#181). What it finds
         * outstanding afterwards is saved for the next poll, plus anything the walk never reached.
         * A poll that did not finish cleanly leaves the set alone, so the next one goes as deep.
         */
        private suspend fun checkOwnRequests(): CheckResult {
            val signals =
                listOf(NotificationSignal.RequestAvailable, NotificationSignal.RequestApproved, NotificationSignal.RequestDeclined)
            val on = signals.filter { prefs.isEnabled(it) }
            if (on.isEmpty()) return CheckResult.Ok
            val read = attempt { feeds.ownRequests(prefs.watchedRequestIds()) }.getOrElse { return it.toResult() }
            val requests = read.rows
            val result = checkOwnRequestSignals(on, requests, read.complete)
            if (result == CheckResult.Ok) {
                prefs.setWatchedRequestIds(read.unaccountedFor + requests.filter { it.isUnresolved() }.map { it.id })
            }
            return result
        }

        private suspend fun checkOwnRequestSignals(
            on: List<NotificationSignal>,
            requests: List<SeerrRequestDto>,
            complete: Boolean,
        ): CheckResult {
            val reached = requests.mapTo(mutableSetOf()) { it.id }.takeUnless { complete }
            return on.fold(CheckResult.Ok) { acc, signal ->
                worstOf(
                    acc,
                    checkTransition(signal, requests.filter { it.isIn(signal) }, reached) { items ->
                        when (signal) {
                            NotificationSignal.RequestAvailable -> notifier.notifyRequestsAvailable(items)
                            NotificationSignal.RequestApproved -> notifier.notifyRequestsApproved(items)
                            else -> notifier.notifyRequestsDeclined(items)
                        }
                    },
                )
            }
        }

        /**
         * Announces the requests in the state whose id is not in the saved set, then saves the current set, so a request
         * that leaves the state drops out. A null set is a seed run. A failed title lookup leaves the set unsaved for a retry.
         * [reached] is the ids a short walk read, null for a full one: a short walk keeps the saved ids it did not reach.
         */
        private suspend fun checkTransition(
            signal: NotificationSignal,
            current: List<SeerrRequestDto>,
            reached: Set<Int>?,
            notify: suspend (List<RequestItem>) -> Unit,
        ): CheckResult {
            val currentIds = current.mapTo(mutableSetOf()) { it.id }
            val seen = prefs.notifiedIds(signal)
            if (seen == null) {
                prefs.setNotifiedIds(signal, currentIds)
                return CheckResult.Ok
            }
            val kept = if (reached == null) currentIds else currentIds + seen.filter { it !in reached }
            val fresh = current.filter { it.id !in seen }
            if (fresh.isNotEmpty()) {
                val titled = attempt { feeds.titled(fresh) }.getOrElse { return it.toResult() }
                if (titled.isNotEmpty()) notify(titled)
            }
            prefs.setNotifiedIds(signal, kept)
            return CheckResult.Ok
        }
    }

/**
 * Still has an outcome to announce. Resolved needs both answers: the media has landed, and the
 * request is no longer waiting on a moderator — a pending request whose media someone else made
 * available can still be approved or declined.
 */
private fun SeerrRequestDto.isUnresolved(): Boolean =
    media.status != SeerrMediaStatusCode.Available || status == null || status == SeerrRequestStatusCode.Pending

private fun SeerrRequestDto.isIn(signal: NotificationSignal): Boolean =
    when (signal) {
        NotificationSignal.RequestAvailable -> media.status == SeerrMediaStatusCode.Available
        NotificationSignal.RequestApproved -> status == SeerrRequestStatusCode.Approved
        NotificationSignal.RequestDeclined -> status == SeerrRequestStatusCode.Declined
        NotificationSignal.PendingRequests, NotificationSignal.OpenIssues -> false
    }

internal fun Throwable.toResult(): CheckResult =
    if (toSeerrError() == SeerrError.Unauthorized) CheckResult.AuthFailure else CheckResult.TransientFailure

private fun worstOf(
    a: CheckResult,
    b: CheckResult,
): CheckResult = if (a.ordinal >= b.ordinal) a else b
