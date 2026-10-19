package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrEditRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestBody
import com.cooper.binge.companion.seerr.seerr.SeerrRequestCountDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestResultDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestSummaryDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestsPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrSeasonStatusDto

/** `request`: the whole life of a request, as the console and the exported Service drive it. */
internal fun DemoData.requestRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "request", "count") { _, _ -> ok(requestCount()) },
        route("GET", "request") { call, _ -> ok(requestPage(call, call.query("requestedBy")?.toIntOrNull())) },
        route("POST", "request") { call, _ -> create(call.body) },
        route("GET", "user", "{}", "requests") { call, (id) -> ok(requestPage(call, id.toInt())) },
        route("GET", "request", "{}") { _, (id) -> withRequest(id) { ok(toDto(it)) } },
        route("PUT", "request", "{}") { call, (id) -> withRequest(id) { edit(it, call.body) } },
        route("DELETE", "request", "{}") { _, (id) -> withRequest(id) { requests.remove(it).let { done } } },
        route("POST", "request", "{}", "{}") { _, (id, action) -> withRequest(id) { moderate(it, action) } },
    )

internal val done = DemoAnswer(200, "{}")

private fun DemoData.withRequest(
    id: String,
    block: (DemoRequest) -> DemoAnswer,
): DemoAnswer = requests.firstOrNull { it.id == id.toInt() }?.let(block) ?: demoNotFound

private fun DemoData.moderate(
    request: DemoRequest,
    action: String,
): DemoAnswer {
    val status =
        when (action) {
            "approve", "retry" -> SeerrRequestStatusCode.Approved
            "decline" -> SeerrRequestStatusCode.Declined
            else -> null
        }
    return status?.let {
        request.status = it
        request.modifiedBy = DEMO_USER_ID
        request.updatedAt = now()
        request.download = it == SeerrRequestStatusCode.Approved
        ok(toDto(request))
    } ?: notInTheDemo
}

private fun DemoData.create(body: String): DemoAnswer {
    val asked = demoJson.decodeFromString<SeerrRequestBody>(body)
    val title = titleByTmdb(asked.mediaType, asked.mediaId)
    return when {
        title == null -> demoNotFound
        mediaStatus(title) == SeerrMediaStatusCode.Blocklisted -> demoBlocklisted
        title.isMovie && requests.any { it.title == title } -> demoAlreadyRequested
        else -> {
            val request = add(title, DEMO_USER_ID, seasons = asked.seasons.orEmpty())
            // An administrator's request needs no approval, as on a real server.
            moderate(request, "approve")
            ok(SeerrRequestResultDto(request.id))
        }
    }
}

private fun DemoData.edit(
    request: DemoRequest,
    body: String,
): DemoAnswer {
    demoJson.decodeFromString<SeerrEditRequestBody>(body).seasons?.let { request.seasons = it }
    request.updatedAt = now()
    return done
}

private fun DemoData.requestPage(
    call: DemoCall,
    requestedBy: Int?,
): SeerrRequestsPageDto {
    val filter = call.query("filter") ?: "all"
    val order =
        if (call.query("sort") ==
            "modified"
        ) {
            compareByDescending<DemoRequest> { it.updatedAt }
        } else {
            compareByDescending { it.createdAt }
        }
    val rows = requests.filter { requestedBy == null || it.requestedBy == requestedBy }.filter { matches(it, filter) }.sortedWith(order)
    val (page, info) = rows.pageFor(call)
    return SeerrRequestsPageDto(pageInfo = info, results = page.map { toDto(it) })
}

internal fun DemoData.matches(
    request: DemoRequest,
    filter: String,
): Boolean {
    val media = mediaStatus(request.title)
    return when (filter) {
        "pending" -> request.status == SeerrRequestStatusCode.Pending
        "approved" -> request.status == SeerrRequestStatusCode.Approved
        "processing" -> request.status == SeerrRequestStatusCode.Approved && media == SeerrMediaStatusCode.Processing
        "available" -> media == SeerrMediaStatusCode.Available || media == SeerrMediaStatusCode.PartiallyAvailable
        "unavailable" -> media != SeerrMediaStatusCode.Available && request.status != SeerrRequestStatusCode.Declined
        "failed" -> request.status == SeerrRequestStatusCode.Failed
        else -> true
    }
}

private fun DemoData.requestCount(): SeerrRequestCountDto =
    SeerrRequestCountDto(
        total = requests.size,
        movie = requests.count { it.title.isMovie },
        tv = requests.count { !it.title.isMovie },
        pending = requests.count { it.status == SeerrRequestStatusCode.Pending },
        approved = requests.count { it.status == SeerrRequestStatusCode.Approved },
        declined = requests.count { it.status == SeerrRequestStatusCode.Declined },
        processing = requests.count { matches(it, "processing") },
        available = requests.count { matches(it, "available") },
    )

internal fun DemoData.mediaOf(title: DemoTitle) =
    SeerrRequestMediaDto(
        id = title.mediaId,
        tmdbId = title.tmdbId,
        mediaType = title.mediaType,
        status = mediaStatus(title),
        downloadStatus = downloads(title),
    )

private fun DemoData.seasonsOf(request: DemoRequest) = request.seasons.map { SeerrSeasonStatusDto(it, status = mediaStatus(request.title)) }

private fun DemoData.toDto(request: DemoRequest) =
    SeerrRequestDto(
        id = request.id,
        status = request.status,
        media = mediaOf(request.title),
        createdAt = request.createdAt.toString(),
        updatedAt = request.updatedAt.toString(),
        requestedBy = user(request.requestedBy),
        modifiedBy = user(request.modifiedBy),
        seasons = seasonsOf(request),
    )

internal fun DemoData.toSummary(request: DemoRequest) =
    SeerrRequestSummaryDto(
        id = request.id,
        status = request.status,
        createdAt = request.createdAt.toString(),
        requestedBy = user(request.requestedBy),
        seasons = seasonsOf(request),
    )
