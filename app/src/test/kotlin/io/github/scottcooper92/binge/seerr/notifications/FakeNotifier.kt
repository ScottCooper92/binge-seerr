package io.github.scottcooper92.binge.seerr.notifications

import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import java.util.Collections

/** Records what the poll would have shown. */
internal class FakeNotifier : SeerrNotifier {
    var canPost = true
    val newRequests = mutableListOf<List<Int>>()
    val newIssues = mutableListOf<List<Int>>()
    val available = mutableListOf<List<Int>>()
    val approved = mutableListOf<List<Int>>()
    val declined = mutableListOf<List<Int>>()
    var connectionProblems = 0
    var connectionProblemCancels = 0
    var activityCancels = 0

    override fun canPost(): Boolean = canPost

    override fun notifyNewRequests(items: List<RequestItem>) {
        newRequests += items.map { it.id }
    }

    override fun notifyNewIssues(items: List<IssueItem>) {
        newIssues += items.map { it.id }
    }

    override fun notifyRequestsAvailable(items: List<RequestItem>) {
        available += items.map { it.id }
    }

    override fun notifyRequestsApproved(items: List<RequestItem>) {
        approved += items.map { it.id }
    }

    override fun notifyRequestsDeclined(items: List<RequestItem>) {
        declined += items.map { it.id }
    }

    override fun notifyConnectionProblem() {
        connectionProblems++
    }

    override fun cancelConnectionProblem() {
        connectionProblemCancels++
    }

    override fun cancelActivity() {
        activityCancels++
    }
}

internal class FakeScheduler : NotificationScheduler {
    /** Synchronized: the planner's own coroutine appends while a test's polling loop reads. */
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun schedule() {
        calls += "schedule"
    }

    override fun cancel() {
        calls += "cancel"
    }
}
