package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrPageInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.homelab.core.server.demo.DemoAnswer
import com.cooper.homelab.core.server.demo.DemoCall
import com.cooper.homelab.core.server.demo.DemoRoute
import com.cooper.homelab.core.server.demo.route
import kotlinx.serialization.json.JsonPrimitive

/** `media`, the title lookups and the download servers: what a request page reads about the title and where it goes. */
internal fun DemoData.mediaRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "movie", "{}") { _, (id) -> movieDetails(id) },
        route("GET", "tv", "{}") { _, (id) -> tvDetails(id) },
        route("POST", "media", "{}", "{}") { _, (id, status) -> withMedia(id) { markMedia(it, status) } },
        route("DELETE", "media", "{}") { _, (id) -> withMedia(id) { forget(it).let { done } } },
        route("DELETE", "media", "{}", "file") { _, (id) -> withMedia(id) { deleteFiles(it) } },
        route("GET", "media") { call, _ -> recentlyAdded(call) },
        route("GET", "media", "{}", "watch_data") { _, (id) -> watchAnswer(id) },
        route("GET", "service", "radarr") { _, _ -> ok(demoServers("radarr")) },
        route("GET", "service", "sonarr") { _, _ -> ok(demoServers("sonarr")) },
        route("GET", "service", "radarr", "{}") { _, (id) -> serverDetails("radarr", id) },
        route("GET", "service", "sonarr", "{}") { _, (id) -> serverDetails("sonarr", id) },
    )

/** A Jellyfin server has no watch counts; the demo answers two titles in its library so the row can be seen, and the rest as before. */
private fun DemoData.watchAnswer(id: String): DemoAnswer {
    val title = id.toIntOrNull()?.let { titleForMedia(it) }
    val watch = title?.let { watchData(it) }
    return watch?.let { ok(it) } ?: demoNotFound
}

private fun DemoData.serverDetails(
    service: String,
    id: String,
): DemoAnswer = id.toIntOrNull()?.let { demoServerDetails(service, it) }?.let { ok(it) } ?: demoNotFound

private fun DemoData.withMedia(
    id: String,
    block: (DemoTitle) -> DemoAnswer,
): DemoAnswer = titleForMedia(id.toInt())?.let(block) ?: demoNotFound

/**
 * Seerr deletes a title's files through its default Radarr or Sonarr, and refuses with a 409 when none is the default (#1726).
 * The demo does the same once its settings pages have un-defaulted them, so the refusal can be walked here with no real
 * server (#1736). As it ships, its one server of each kind is the default, so a delete goes through.
 */
private fun DemoData.deleteFiles(title: DemoTitle): DemoAnswer {
    val service = if (title.isMovie) "radarr" else "sonarr"
    val hasDefault = resources.lists[service].orEmpty().any { it["isDefault"] == JsonPrimitive(true) && it["is4k"] != JsonPrimitive(true) }
    return if (hasDefault) forget(title).let { done } else demoNoDefaultServer(if (title.isMovie) "Radarr" else "Sonarr")
}

/** The body only names the 4K instance, which the demo does not have, so it is not read. */
private fun DemoData.markMedia(
    title: DemoTitle,
    path: String,
): DemoAnswer {
    val status = mediaStatusFor(path)
    return if (status == null) {
        notInTheDemo
    } else {
        setMediaStatus(title, status)
        done
    }
}

private fun mediaStatusFor(path: String): SeerrMediaStatusCode? =
    when (path) {
        "available" -> SeerrMediaStatusCode.Available
        "partial" -> SeerrMediaStatusCode.PartiallyAvailable
        "processing" -> SeerrMediaStatusCode.Processing
        "pending" -> SeerrMediaStatusCode.Pending
        "unknown" -> SeerrMediaStatusCode.Unknown
        else -> null
    }

internal fun DemoData.details(
    mediaType: String,
    id: String,
): DemoAnswer {
    val title = titleFor(mediaType, id.toInt())
    val status = mediaStatus(title)
    val added = mediaAddedAt(title)
    val info =
        SeerrMediaInfoDto(
            status4k = mediaStatus4k(title),
            seasons = seasonStatuses(title),
            downloadStatus = downloads(title),
            downloadStatus4k = downloads4k(title),
            requests = requests.filter { it.title == title }.map { toSummary(it) },
            mediaUrl = added?.let { "$DEMO_MEDIA_SERVER/web/index.html#!/details?id=${jellyfinId(title)}" },
            jellyfinMediaId = added?.let { jellyfinId(title) },
            mediaAddedAt = added,
        )
    return ok(title.details(status, info))
}

/** The demo's own Jellyfin item id for a title: a made-up one, so no real server is named. */
private fun jellyfinId(title: DemoTitle) = "demo-item-${title.mediaId}"

private const val DEMO_MEDIA_SERVER = "https://jellyfin.demo.seerr.invalid"

/** The titles the library holds, newest first, as `media` lists them: what the hubs' Recently Added row reads. */
private fun DemoData.recentlyAdded(call: DemoCall): DemoAnswer {
    val take = call.intQuery("take", DEFAULT_MEDIA_TAKE).coerceAtLeast(1)
    val held = DEMO_TITLES.filter { mediaStatus(it) == SeerrMediaStatusCode.Available }.sortedByDescending { it.mediaId }
    val rows =
        held.take(take).map {
            SeerrRequestMediaDto(id = it.mediaId, tmdbId = it.tmdbId, mediaType = it.mediaType, status = SeerrMediaStatusCode.Available)
        }
    return ok(SeerrMediaPageDto(pageInfo = SeerrPageInfoDto(results = held.size, pages = 1, page = 1), results = rows))
}

private const val DEFAULT_MEDIA_TAKE = 20
