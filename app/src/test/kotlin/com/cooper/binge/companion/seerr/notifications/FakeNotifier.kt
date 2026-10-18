package com.cooper.binge.companion.seerr.notifications

import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    override suspend fun canPost(): Boolean = canPost

    override suspend fun notifyNewRequests(items: List<RequestItem>) {
        newRequests += items.map { it.id }
    }

    override suspend fun notifyNewIssues(items: List<IssueItem>) {
        newIssues += items.map { it.id }
    }

    override suspend fun notifyRequestsAvailable(items: List<RequestItem>) {
        available += items.map { it.id }
    }

    override suspend fun notifyRequestsApproved(items: List<RequestItem>) {
        approved += items.map { it.id }
    }

    override suspend fun notifyRequestsDeclined(items: List<RequestItem>) {
        declined += items.map { it.id }
    }

    override suspend fun notifyConnectionProblem() {
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
    val nextRun = MutableStateFlow<Long?>(null)

    override fun schedule() {
        calls += "schedule"
    }

    override fun cancel() {
        calls += "cancel"
    }

    override fun nextRunMillis(): Flow<Long?> = nextRun
}
