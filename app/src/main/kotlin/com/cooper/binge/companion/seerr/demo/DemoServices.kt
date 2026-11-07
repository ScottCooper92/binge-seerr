package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrProfileDto
import com.cooper.binge.companion.seerr.seerr.SeerrRootFolderDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerRefDto
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import kotlinx.serialization.json.decodeFromJsonElement

internal const val DEMO_SERVER_ID = 1
internal const val DEMO_PROFILE_ID = 1

/** The second profile the demo's *arr offers; a few requests are for it so the labels differ. */
internal const val DEMO_HD_PROFILE_ID = 2

/** Where each kind's demo server keeps its media; the seed sets the server to this folder, so what it is set to is always offered. */
internal fun demoRootFolder(service: String) = if (service == "radarr") "/media/movies" else "/media/tv"

internal fun demoRootFolders(service: String) = listOf(SeerrRootFolderDto(1, demoRootFolder(service)))

/**
 * The download servers of a kind, read from the settings pages' own list, so a server the user added, renamed or
 * un-defaulted there is the one the advanced request offers (#1820). Nothing downloads: they are names and defaults.
 */
internal fun DemoData.demoServers(service: String) =
    storedServers(service).map {
        SeerrServerDto(
            id = it.id ?: 0,
            name = it.name.orEmpty(),
            is4k = it.is4k,
            isDefault = it.isDefault,
            activeProfileId = it.activeProfileId,
            activeDirectory = it.activeDirectory,
        )
    }

/** One server's details: its own entry, with the profiles and folders the demo's *arr offers, or null for an unknown id. */
internal fun DemoData.demoServerDetails(
    service: String,
    id: Int,
): SeerrServerDetailsDto? =
    storedServers(service).firstOrNull { it.id == id }?.let {
        SeerrServerDetailsDto(
            server = SeerrServerRefDto(id, it.name.orEmpty()),
            profiles = listOf(SeerrProfileDto(DEMO_PROFILE_ID, "Any"), SeerrProfileDto(DEMO_HD_PROFILE_ID, "HD-1080p")),
            rootFolders = demoRootFolders(service),
        )
    }

private fun DemoData.storedServers(service: String): List<SeerrServiceSettingsDto> =
    resources.lists[service].orEmpty().map { demoJson.decodeFromJsonElement<SeerrServiceSettingsDto>(it) }
