package io.github.scottcooper92.binge.seerr.notifications

import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import javax.inject.Qualifier

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
