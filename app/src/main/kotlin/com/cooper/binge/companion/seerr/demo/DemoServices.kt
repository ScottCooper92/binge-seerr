package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrProfileDto
import com.cooper.binge.companion.seerr.seerr.SeerrRootFolderDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerRefDto

private const val SERVER_ID = 1
private const val PROFILE_ID = 1

/** The one download server of a kind, so the advanced request has something to offer. It is a name only: nothing downloads. */
internal fun demoServers(name: String) =
    listOf(SeerrServerDto(id = SERVER_ID, name = name, isDefault = true, activeProfileId = PROFILE_ID, activeDirectory = "/media"))

internal fun demoServerDetails(name: String) =
    SeerrServerDetailsDto(
        server = SeerrServerRefDto(SERVER_ID, name),
        profiles = listOf(SeerrProfileDto(PROFILE_ID, "Any"), SeerrProfileDto(2, "HD-1080p")),
        rootFolders = listOf(SeerrRootFolderDto(1, "/media")),
    )
