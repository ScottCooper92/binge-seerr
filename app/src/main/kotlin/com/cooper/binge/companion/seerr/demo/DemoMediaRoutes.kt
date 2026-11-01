package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import kotlinx.serialization.json.JsonPrimitive

/** `media`, the title lookups and the download servers: what a request page reads about the title and where it goes. */
internal fun DemoData.mediaRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "movie", "{}") { _, (id) -> details("movie", id) },
        route("GET", "tv", "{}") { _, (id) -> details("tv", id) },
        route("POST", "media", "{}", "{}") { _, (id, status) -> withMedia(id) { markMedia(it, status) } },
        route("DELETE", "media", "{}") { _, (id) -> withMedia(id) { forget(it).let { done } } },
        route("DELETE", "media", "{}", "file") { _, (id) -> withMedia(id) { deleteFiles(it) } },
        route("GET", "media", "{}", "watch_data") { _, _ -> demoNotFound },
        route("GET", "service", "radarr") { _, _ -> ok(demoServers("radarr")) },
        route("GET", "service", "sonarr") { _, _ -> ok(demoServers("sonarr")) },
        route("GET", "service", "radarr", "{}") { _, (id) -> serverDetails("radarr", id) },
        route("GET", "service", "sonarr", "{}") { _, (id) -> serverDetails("sonarr", id) },
    )

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
    val hasDefault = resources.lists[service].orEmpty().any { it["isDefault"] == JsonPrimitive(true) }
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

private fun DemoData.details(
    mediaType: String,
    id: String,
): DemoAnswer {
    val title = titleFor(mediaType, id.toInt())
    val info =
        SeerrMediaInfoDto(
            downloadStatus = downloads(title),
            requests = requests.filter { it.title == title }.map { toSummary(it) },
        )
    return ok(title.details(mediaStatus(title), info))
}
