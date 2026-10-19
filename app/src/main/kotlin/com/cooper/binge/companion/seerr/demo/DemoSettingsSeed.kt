package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrDefaultQuotaDto
import com.cooper.binge.companion.seerr.seerr.SeerrDefaultQuotasDto
import com.cooper.binge.companion.seerr.seerr.SeerrDiscoverSliderDto
import com.cooper.binge.companion.seerr.seerr.SeerrJellyfinSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrLibraryDto
import com.cooper.binge.companion.seerr.seerr.SeerrMainSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMetadataSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrNetworkSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrOverrideRuleDto
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrTautulliSettingsDto

private const val REQUEST_PERMISSION = 32
private const val JELLYFIN_PORT = 8096
private const val RADARR_PORT = 7878
private const val SONARR_PORT = 8989
private const val DEFAULT_QUOTA_DAYS = 7
private const val BLOCKLIST_TAG_LIMIT = 50

/** The pages and lists a first look at the settings screens finds: plain defaults and one of each service, none of them real. */
internal fun seedResources(): DemoResources =
    DemoResources().apply {
        objects["main"] = toJson(mainSettings())
        objects["jellyfin"] = toJson(jellyfin())
        objects["tautulli"] = toJson(SeerrTautulliSettingsDto(hostname = "", port = 8181, useSsl = false))
        objects["network"] = toJson(SeerrNetworkSettingsDto(csrfProtection = false, trustProxy = false, forceIpv4First = false))
        objects["metadatas"] = toJson(SeerrMetadataSettingsDto(tv = "tvdb", anime = "tvdb"))
        lists["radarr"] = mutableListOf(toJson(dvr(1, "Demo Radarr", RADARR_PORT, "/media/movies")))
        lists["sonarr"] = mutableListOf(toJson(dvr(1, "Demo Sonarr", SONARR_PORT, "/media/tv")))
        lists["rules"] = mutableListOf(toJson(SeerrOverrideRuleDto(id = 1, radarrServiceId = 1, genre = "16", profileId = 2)))
        lists["sliders"] = seedSliders()
    }

private fun mainSettings() =
    SeerrMainSettingsDto(
        apiKey = "demo-api-key-not-a-secret",
        applicationTitle = "Seerr demo",
        appLanguage = "en",
        locale = "en",
        region = "",
        discoverRegion = "",
        streamingRegion = "",
        originalLanguage = "",
        hideAvailable = false,
        hideRequested = false,
        partialRequestsEnabled = true,
        enableSpecialEpisodes = false,
        versionCheck = true,
        cacheImages = false,
        youtubeUrl = "",
        trustProxy = false,
        csrfProtection = false,
        defaultPermissions = REQUEST_PERMISSION,
        defaultQuotas =
            SeerrDefaultQuotasDto(
                movie = SeerrDefaultQuotaDto(quotaLimit = 0, quotaDays = DEFAULT_QUOTA_DAYS),
                tv = SeerrDefaultQuotaDto(quotaLimit = 0, quotaDays = DEFAULT_QUOTA_DAYS),
            ),
        localLogin = true,
        mediaServerLogin = true,
        newPlexLogin = false,
        hideBlocklisted = false,
        blocklistRegion = "",
        blocklistLanguage = "",
        blocklistedTags = "",
        blocklistedTagsLimit = BLOCKLIST_TAG_LIMIT,
    )

private fun jellyfin() =
    SeerrJellyfinSettingsDto(
        name = "Demo library",
        ip = "jellyfin.invalid",
        port = JELLYFIN_PORT,
        useSsl = false,
        urlBase = "",
        externalHostname = "",
        libraries = demoLibraries(),
        serverId = "demo",
    )

internal fun demoLibraries() =
    listOf(
        SeerrLibraryDto(id = "movies", name = "Movies", enabled = true, type = "movie"),
        SeerrLibraryDto(id = "shows", name = "Shows", enabled = true, type = "show"),
    )

private fun dvr(
    id: Int,
    name: String,
    port: Int,
    directory: String,
) = SeerrServiceSettingsDto(
    id = id,
    name = name,
    hostname = "${name.substringAfter(' ').lowercase()}.invalid",
    port = port,
    apiKey = "demo",
    activeProfileId = 1,
    activeProfileName = "Any",
    activeDirectory = directory,
    isDefault = true,
)

private fun seedSliders(): MutableList<kotlinx.serialization.json.JsonObject> =
    mutableListOf(
        slider(id = 1, type = 1),
        slider(id = 2, type = 2),
        slider(id = 3, type = 3),
        slider(id = 4, type = 4),
        slider(id = 5, type = 5),
    )

private fun slider(
    id: Int,
    type: Int,
) = toJson(SeerrDiscoverSliderDto(id = id, type = type, isBuiltIn = true, enabled = true))
