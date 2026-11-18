package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrCreateIssueBody
import com.cooper.binge.companion.seerr.seerr.SeerrIssueCommentBody
import com.cooper.binge.companion.seerr.seerr.SeerrIssueCountDto
import com.cooper.binge.companion.seerr.seerr.SeerrIssueDto
import com.cooper.binge.companion.seerr.seerr.SeerrIssuePageDto
import com.cooper.binge.companion.seerr.seerr.SeerrIssueStatusCode

/** `issue` and `issueComment`: reporting, the thread under an issue, and resolving it. */
internal fun DemoData.issueRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "issue", "count") { _, _ -> ok(issueCount()) },
        route("GET", "issue") { call, _ -> ok(issuePage(call)) },
        route("POST", "issue") { call, _ -> createIssue(call.body) },
        route("GET", "issue", "{}") { _, (id) -> withIssue(id) { ok(toDto(it)) } },
        route("DELETE", "issue", "{}") { _, (id) -> withIssue(id) { issues.remove(it).let { done } } },
        route("POST", "issue", "{}", "comment") { call, (id) -> withIssue(id) { post(it, call.body) } },
        route("POST", "issue", "{}", "{}") { _, (id, status) -> withIssue(id) { setStatus(it, status) } },
        route("PUT", "issueComment", "{}") { call, (id) -> editComment(id.toInt(), call.body) },
        route("DELETE", "issueComment", "{}") { _, (id) -> deleteComment(id.toInt()) },
    )

private fun DemoData.withIssue(
    id: String,
    block: (DemoIssue) -> DemoAnswer,
): DemoAnswer = issues.firstOrNull { it.id == id.toInt() }?.let(block) ?: demoNotFound

private fun DemoData.issueCount(): SeerrIssueCountDto =
    SeerrIssueCountDto(
        total = issues.size,
        open = issues.count { it.status == SeerrIssueStatusCode.Open },
        closed = issues.count { it.status == SeerrIssueStatusCode.Resolved },
    )

private fun DemoData.issuePage(call: DemoCall): SeerrIssuePageDto {
    val filter = call.query("filter") ?: "all"
    val order =
        if (call.query("sort") ==
            "modified"
        ) {
            compareByDescending<DemoIssue> { it.updatedAt }
        } else {
            compareByDescending { it.createdAt }
        }
    val rows =
        issues
            .filter {
                when (filter) {
                    "open" -> it.status == SeerrIssueStatusCode.Open
                    "resolved" -> it.status == SeerrIssueStatusCode.Resolved
                    else -> true
                }
            }.sortedWith(order)
    val (page, info) = rows.pageFor(call)
    return SeerrIssuePageDto(pageInfo = info, results = page.map { toDto(it) })
}

private fun DemoData.createIssue(body: String): DemoAnswer {
    val asked = demoJson.decodeFromString<SeerrCreateIssueBody>(body)
    val title = titleForMedia(asked.mediaId) ?: return demoNotFound
    val issue = addIssue(title, asked.issueType, DEMO_USER_ID, asked.message)
    return ok(toDto(issue))
}

private fun DemoData.post(
    issue: DemoIssue,
    body: String,
): DemoAnswer {
    comment(issue, DEMO_USER_ID, demoJson.decodeFromString<SeerrIssueCommentBody>(body).message)
    return ok(toDto(issue))
}

private fun DemoData.setStatus(
    issue: DemoIssue,
    status: String,
): DemoAnswer {
    issue.status =
        when (status) {
            "resolved" -> SeerrIssueStatusCode.Resolved
            "open" -> SeerrIssueStatusCode.Open
            else -> return notInTheDemo
        }
    issue.updatedAt = now()
    return ok(toDto(issue))
}

private fun DemoData.editComment(
    id: Int,
    body: String,
): DemoAnswer {
    val message = demoJson.decodeFromString<SeerrIssueCommentBody>(body).message
    for (issue in issues) {
        val at = issue.comments.indexOfFirst { it.id == id }
        if (at >= 0) {
            issue.comments[at] = issue.comments[at].copy(message = message, updatedAt = now().toString())
            return done
        }
    }
    return demoNotFound
}

private fun DemoData.deleteComment(id: Int): DemoAnswer =
    if (issues.any { issue -> issue.comments.removeAll { it.id == id } }) done else demoNotFound

private fun DemoData.toDto(issue: DemoIssue) =
    SeerrIssueDto(
        id = issue.id,
        issueType = issue.type,
        status = issue.status,
        createdAt = issue.createdAt.toString(),
        updatedAt = issue.updatedAt.toString(),
        createdBy = user(issue.createdBy),
        media = mediaOf(issue.title),
        comments = issue.comments.toList(),
    )
