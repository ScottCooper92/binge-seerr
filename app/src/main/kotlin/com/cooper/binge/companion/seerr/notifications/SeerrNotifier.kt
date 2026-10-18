package com.cooper.binge.companion.seerr.notifications

import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import javax.inject.Qualifier

/** The scope that outlives every screen, for the planner. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/** What the poll tells the user, seamed off Android so the checker is tested against a fake. */
interface SeerrNotifier {
    /** Whether anything posted could be shown; the poll does no work when it could not. */
    suspend fun canPost(): Boolean

    suspend fun notifyNewRequests(items: List<RequestItem>)

    suspend fun notifyNewIssues(items: List<IssueItem>)

    suspend fun notifyRequestsAvailable(items: List<RequestItem>)

    suspend fun notifyRequestsApproved(items: List<RequestItem>)

    suspend fun notifyRequestsDeclined(items: List<RequestItem>)

    /** The server rejected the saved credentials; the user has to sign in again. */
    suspend fun notifyConnectionProblem()

    fun cancelConnectionProblem()

    /** Every activity notice posted so far, for a disconnect: they would open pages of a server that is gone. */
    fun cancelActivity()
}
