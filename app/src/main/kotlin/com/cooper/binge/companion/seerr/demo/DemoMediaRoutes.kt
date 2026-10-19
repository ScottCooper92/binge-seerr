package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode

/** `media`, the title lookups and the download servers: what a request page reads about the title and where it goes. */
internal fun DemoData.mediaRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "movie", "{}") { _, (id) -> details("movie", id) },
        route("GET", "tv", "{}") { _, (id) -> details("tv", id) },
        route("POST", "media", "{}", "{}") { _, (id, status) -> withMedia(id) { markMedia(it, status) } },
        route("DELETE", "media", "{}") { _, (id) -> withMedia(id) { forget(it).let { done } } },
        route("DELETE", "media", "{}", "file") { _, (id) -> withMedia(id) { forget(it).let { done } } },
        route("GET", "media", "{}", "watch_data") { _, _ -> demoNotFound },
        route("GET", "service", "radarr") { _, _ -> ok(demoServers("Demo Radarr")) },
        route("GET", "service", "sonarr") { _, _ -> ok(demoServers("Demo Sonarr")) },
        route("GET", "service", "radarr", "{}") { _, _ -> ok(demoServerDetails("Demo Radarr")) },
        route("GET", "service", "sonarr", "{}") { _, _ -> ok(demoServerDetails("Demo Sonarr")) },
    )

private fun withMedia(
    id: String,
    block: (DemoTitle) -> DemoAnswer,
): DemoAnswer = titleByMediaId(id.toInt())?.let(block) ?: demoNotFound

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
    val title = titleByTmdb(mediaType, id.toInt()) ?: return demoNotFound
    val info =
        SeerrMediaInfoDto(
            downloadStatus = downloads(title),
            requests = requests.filter { it.title == title }.map { toSummary(it) },
        )
    return ok(title.details(mediaStatus(title), info))
}
