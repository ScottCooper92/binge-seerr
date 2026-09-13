package io.github.scottcooper92.binge.seerr.notifications

import android.util.Log
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import javax.inject.Qualifier

private const val TAG = "SeerrNotifier"

/** The scope that outlives every screen, for the planner. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/** What the poll tells the user, seamed off Android so the checker is tested against a fake. */
interface SeerrNotifier {
    /** Whether anything posted could be shown; the poll does no work when it could not. */
    fun canPost(): Boolean

    fun notifyNewRequests(items: List<RequestItem>)

    fun notifyNewIssues(items: List<IssueItem>)

    fun notifyRequestsAvailable(items: List<RequestItem>)

    fun notifyRequestsApproved(items: List<RequestItem>)

    fun notifyRequestsDeclined(items: List<RequestItem>)

    /** The server rejected the saved credentials; the user has to sign in again. */
    fun notifyConnectionProblem()

    fun cancelConnectionProblem()

    /** Every activity notice posted so far, for a disconnect: they would open pages of a server that is gone. */
    fun cancelActivity()
}

/** Logs what would be notified; the channels, the content and the deep links arrive with Phase 5.2. */
class LogNotifier : SeerrNotifier {
    override fun canPost(): Boolean = true

    override fun notifyNewRequests(items: List<RequestItem>) = log("new requests", items.map { it.id })

    override fun notifyNewIssues(items: List<IssueItem>) = log("new issues", items.map { it.id })

    override fun notifyRequestsAvailable(items: List<RequestItem>) = log("requests available", items.map { it.id })

    override fun notifyRequestsApproved(items: List<RequestItem>) = log("requests approved", items.map { it.id })

    override fun notifyRequestsDeclined(items: List<RequestItem>) = log("requests declined", items.map { it.id })

    override fun notifyConnectionProblem() = log("connection problem", emptyList())

    override fun cancelConnectionProblem() = Unit

    override fun cancelActivity() = Unit

    private fun log(
        what: String,
        ids: List<Int>,
    ) {
        Log.i(TAG, "$what: $ids")
    }
}
