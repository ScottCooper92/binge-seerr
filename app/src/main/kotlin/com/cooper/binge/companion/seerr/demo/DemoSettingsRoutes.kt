package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrAboutDto
import com.cooper.binge.companion.seerr.seerr.SeerrAppDataDto
import com.cooper.binge.companion.seerr.seerr.SeerrDvrTestResultDto
import com.cooper.binge.companion.seerr.seerr.SeerrLibraryDto
import com.cooper.binge.companion.seerr.seerr.SeerrMetadataTestResultDto
import com.cooper.binge.companion.seerr.seerr.SeerrMetadataTestsDto
import com.cooper.binge.companion.seerr.seerr.SeerrProfileDto
import com.cooper.binge.companion.seerr.seerr.SeerrRootFolderDto
import com.cooper.binge.companion.seerr.seerr.SeerrScanStatusDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** The server's settings pages: general, media server, services, rules, network, discover sliders and notifications. */
internal fun DemoData.settingsRoutes(): List<DemoRoute> =
    resources.run {
        objectRoutes("main", "settings", "main") +
            objectRoutes("jellyfin", "settings", "jellyfin") +
            objectRoutes("tautulli", "settings", "tautulli") +
            objectRoutes("network", "settings", "network") +
            objectRoutes("metadatas", "settings", "metadatas", write = "PUT") +
            listRoutes("rules", "overrideRule") +
            serviceRoutes("radarr") +
            serviceRoutes("sonarr") +
            sliderRoutes() +
            libraryRoutes() +
            aboutRoutes() +
            listOf(
                route("POST", "settings", "main", "regenerate") { _, _ -> regenerateKey() },
                route("POST", "settings", "metadatas", "test") { _, _ -> ok(metadataTest()) },
            )
    }

private fun DemoResources.regenerateKey(): DemoAnswer {
    val main = objects.getValue("main")
    objects["main"] = JsonObject(main + ("apiKey" to JsonPrimitive("demo-api-key-${nextId()}")))
    return jsonAnswer(objects.getValue("main"))
}

private fun metadataTest() = SeerrMetadataTestResultDto(success = true, tests = SeerrMetadataTestsDto(tmdb = "OK", tvdb = "OK"))

/** A download server's list and its edits, and the connection test, which always finds the demo's one profile and folder. */
private fun DemoResources.serviceRoutes(service: String): List<DemoRoute> =
    listRoutes(service, "settings", service) +
        route("POST", "settings", service, "test") { _, _ ->
            ok(
                SeerrDvrTestResultDto(
                    profiles = listOf(SeerrProfileDto(1, "Any"), SeerrProfileDto(2, "HD-1080p")),
                    rootFolders = listOf(SeerrRootFolderDto(1, "/media")),
                ),
            )
        }

/** The home page's sliders: reorder and switch them with one write, add a custom one, edit it, or reset the lot. */
private fun DemoResources.sliderRoutes(): List<DemoRoute> =
    listRoutes("sliders", "settings", "discover", "add").filter { it.verb == "POST" } +
        listOf(
            route("GET", "settings", "discover") { _, _ -> jsonAnswer(JsonArray(lists.getValue("sliders"))) },
            route("POST", "settings", "discover") { call, _ -> replaceSliders(call.body) },
            route("PUT", "settings", "discover", "{}") { call, (id) -> editSlider(id.toInt(), call.body) },
            route("DELETE", "settings", "discover", "{}") { _, (id) -> removeSlider(id.toInt()) },
            route("GET", "settings", "discover", "reset") { _, _ -> resetSliders() },
        )

private fun DemoResources.replaceSliders(body: String): DemoAnswer {
    val sent = demoJson.parseToJsonElement(body) as? JsonArray ?: return notInTheDemo
    lists["sliders"] = sent.filterIsInstance<JsonObject>().toMutableList()
    return jsonAnswer(sent)
}

private fun DemoResources.editSlider(
    id: Int,
    body: String,
): DemoAnswer {
    val sliders = lists.getValue("sliders")
    val at = sliders.indexOfFirst { it["id"].toString() == id.toString() }
    if (at < 0) return demoNotFound
    sliders[at] = JsonObject(sliders[at] + parseObject(body))
    return jsonAnswer(sliders[at])
}

private fun DemoResources.removeSlider(id: Int): DemoAnswer =
    if (lists.getValue("sliders").removeAll { it["id"].toString() == id.toString() }) done else demoNotFound

private fun DemoResources.resetSliders(): DemoAnswer {
    lists["sliders"] = seedResources().lists.getValue("sliders")
    return done
}

/** The media server's libraries, switched on and off, and the scan that reads them. The demo has only the one server. */
private fun DemoResources.libraryRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "settings", "jellyfin", "library") { _, _ -> ok(libraries()) },
        route("PUT", "settings", "jellyfin", "library", "{}") { call, (id) -> toggleLibrary(id, call.body) },
        route("POST", "settings", "jellyfin", "library", "sync") { _, _ -> ok(libraries()) },
        route("GET", "settings", "jellyfin", "sync") { _, _ -> ok(SeerrScanStatusDto(libraries = libraries())) },
        route("POST", "settings", "jellyfin", "sync") { _, _ -> ok(SeerrScanStatusDto(libraries = libraries())) },
    )

private fun DemoResources.libraries(): List<SeerrLibraryDto> =
    (objects.getValue("jellyfin")["libraries"] as? JsonArray)
        ?.map { demoJson.decodeFromJsonElement<SeerrLibraryDto>(it) }
        .orEmpty()

private fun DemoResources.toggleLibrary(
    id: String,
    body: String,
): DemoAnswer {
    val enabled = parseObject(body)["enabled"] ?: return notInTheDemo
    val updated =
        (objects.getValue("jellyfin")["libraries"] as? JsonArray).orEmpty().map { library ->
            val item = library as JsonObject
            if (item["id"] == JsonPrimitive(id)) JsonObject(item + ("enabled" to enabled)) else item
        }
    objects["jellyfin"] = JsonObject(objects.getValue("jellyfin") + ("libraries" to JsonArray(updated)))
    return libraries().firstOrNull { it.id == id }?.let { ok(it) } ?: demoNotFound
}

private fun DemoData.aboutRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "settings", "about") { _, _ ->
            ok(
                SeerrAboutDto(
                    version = DEMO_VERSION,
                    totalRequests = requests.size,
                    totalMediaItems = DEMO_TITLES.size,
                    tz = "UTC",
                    appDataPath = APP_DATA,
                ),
            )
        },
        route(
            "GET",
            "status",
            "appdata",
        ) { _, _ -> ok(SeerrAppDataDto(appData = true, appDataPath = APP_DATA, appDataPermissions = true)) },
    )

private const val APP_DATA = "/app/config"
