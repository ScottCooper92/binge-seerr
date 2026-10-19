package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrAddToBlocklistBody
import com.cooper.binge.companion.seerr.seerr.SeerrBlocklistEntryDto
import com.cooper.binge.companion.seerr.seerr.SeerrBlocklistPageDto

private const val CREATED = 201

/** The blocklist: what is on it, blocking a title, and unblocking one by its TMDB id. */
internal fun DemoData.blocklistRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "blocklist") { call, _ -> ok(blocklistPage(call)) },
        route("POST", "blocklist") { call, _ -> block(call.body) },
        route("DELETE", "blocklist", "{}") { _, (tmdbId) -> if (unblock(tmdbId.toInt())) done else demoNotFound },
    )

private fun DemoData.blocklistPage(call: DemoCall): SeerrBlocklistPageDto {
    val search = call.query("search")?.takeIf { it.isNotBlank() }
    val rows = blocklist.filter { search == null || it.title.title.contains(search, ignoreCase = true) }.sortedByDescending { it.createdAt }
    val (page, info) = rows.pageFor(call)
    return SeerrBlocklistPageDto(
        pageInfo = info,
        results =
            page.map {
                SeerrBlocklistEntryDto(
                    id = it.id,
                    tmdbId = it.title.tmdbId,
                    mediaType = it.title.mediaType,
                    title = it.title.title,
                    createdAt = it.createdAt.toString(),
                    user = user(it.blockedBy),
                )
            },
    )
}

private fun DemoData.block(body: String): DemoAnswer {
    val asked = demoJson.decodeFromString<SeerrAddToBlocklistBody>(body)
    val title = titleByTmdb(asked.mediaType, asked.tmdbId) ?: return demoNotFound
    block(title, asked.user)
    return DemoAnswer(CREATED, "{}")
}
