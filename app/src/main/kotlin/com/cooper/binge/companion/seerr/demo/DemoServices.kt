package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrProfileDto
import com.cooper.binge.companion.seerr.seerr.SeerrRootFolderDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerRefDto

internal const val DEMO_SERVER_ID = 1
internal const val DEMO_PROFILE_ID = 1

/** The second profile the demo's *arr offers; a few requests are for it so the labels differ. */
internal const val DEMO_HD_PROFILE_ID = 2

/** The one download server of a kind, so the advanced request has something to offer. It is a name only: nothing downloads. */
internal fun demoServers(name: String) =
    listOf(
        SeerrServerDto(id = DEMO_SERVER_ID, name = name, isDefault = true, activeProfileId = DEMO_PROFILE_ID, activeDirectory = "/media"),
    )

internal fun demoServerDetails(name: String) =
    SeerrServerDetailsDto(
        server = SeerrServerRefDto(DEMO_SERVER_ID, name),
        profiles = listOf(SeerrProfileDto(DEMO_PROFILE_ID, "Any"), SeerrProfileDto(DEMO_HD_PROFILE_ID, "HD-1080p")),
        rootFolders = listOf(SeerrRootFolderDto(1, "/media")),
    )
