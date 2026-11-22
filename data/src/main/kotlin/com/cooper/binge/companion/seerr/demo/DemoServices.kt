package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrProfileDto
import com.cooper.binge.companion.seerr.seerr.SeerrRootFolderDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerRefDto
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrTagDto
import kotlinx.serialization.json.decodeFromJsonElement

internal const val DEMO_SERVER_ID = 1

/** The 4K server each kind offers, which a 4K request is sent to. */
internal const val DEMO_4K_SERVER_ID = 2

internal const val DEMO_PROFILE_ID = 1

/** The second profile the demo's *arr offers; a few requests are for it so the labels differ. */
internal const val DEMO_HD_PROFILE_ID = 2

/** Where each kind's demo server keeps its media; the seed sets the server to this folder, so what it is set to is always offered. */
internal fun demoRootFolder(service: String) = if (service == "radarr") "/media/movies" else "/media/tv"

/** Free space of the demo's main folder, 1.2 TB as the app's size formatter reads it (it counts in 1024s). */
internal const val DEMO_FOLDER_FREE_BYTES = 1_319_413_953_331L

/** The second folder's free space, 340 GB in the same terms, so the two rows differ. */
internal const val DEMO_ARCHIVE_FREE_BYTES = 365_072_220_160L

internal fun demoRootFolders(service: String) =
    listOf(
        SeerrRootFolderDto(1, demoRootFolder(service), DEMO_FOLDER_FREE_BYTES, DEMO_FOLDER_FREE_BYTES * 2),
        SeerrRootFolderDto(2, demoRootFolder(service) + "-archive", DEMO_ARCHIVE_FREE_BYTES, DEMO_FOLDER_FREE_BYTES),
    )

/** The tags the demo's *arr offers. The first is on the default server, so a plain request has a default to show. */
internal val DEMO_TAGS = listOf(SeerrTagDto(1, "family"), SeerrTagDto(2, "anime"), SeerrTagDto(3, "kids"))

/** The tag the demo's default server puts on a plain request. */
internal const val DEMO_DEFAULT_TAG_ID = 1

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
            server =
                SeerrServerRefDto(
                    id = id,
                    name = it.name.orEmpty(),
                    activeProfileId = it.activeProfileId,
                    activeDirectory = it.activeDirectory,
                    activeTags = if (it.isDefault && !it.is4k) listOf(DEMO_DEFAULT_TAG_ID) else emptyList(),
                ),
            profiles = listOf(SeerrProfileDto(DEMO_PROFILE_ID, "Any"), SeerrProfileDto(DEMO_HD_PROFILE_ID, "HD-1080p")),
            rootFolders = demoRootFolders(service),
            tags = DEMO_TAGS,
        )
    }

private fun DemoData.storedServers(service: String): List<SeerrServiceSettingsDto> =
    resources.lists[service].orEmpty().map { demoJson.decodeFromJsonElement<SeerrServiceSettingsDto>(it) }
