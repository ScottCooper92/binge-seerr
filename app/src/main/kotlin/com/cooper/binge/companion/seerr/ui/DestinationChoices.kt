package com.cooper.binge.companion.seerr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.seerr.seerr.SeerrServerDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerRefDto

/**
 * Where a request goes: the instances it may go to, and the chosen one's profiles, root folders and
 * tags. The request editor sequences the two fetches with [onServer] and [withChoices]; the advanced-request
 * RPCs apply the same rule on their own side, in `destinationChoicesFor`.
 *
 * [loadingChoices] is true while the chosen instance is being read, so a screen can hold the submit.
 */
data class DestinationChoices(
    val servers: List<Choice> = emptyList(),
    val serverId: Int? = null,
    val profiles: List<Choice> = emptyList(),
    val profileId: Int? = null,
    val rootFolders: List<String> = emptyList(),
    val rootFolder: String? = null,
    val tags: List<Choice> = emptyList(),
    val tagIds: Set<Int> = emptySet(),
    /** The profile a plain request to the chosen instance gets, and the bytes free per root folder where it reported them. */
    val defaultProfileId: Int? = null,
    /** Seerr treats this series as anime, so an instance's anime profile and folder are its defaults. */
    val anime: Boolean = false,
    val freeSpace: Map<String, Long> = emptyMap(),
    val loadingChoices: Boolean = false,
) {
    /**
     * Moving to another instance: its own defaults, and none of the previous one's choices. A
     * profile id or a tag means nothing on a different instance, so they are dropped rather than
     * carried and later found missing.
     */
    fun onServer(server: SeerrServerDto): DestinationChoices =
        copy(
            serverId = server.id,
            profiles = emptyList(),
            profileId = server.defaultProfile(anime),
            defaultProfileId = server.defaultProfile(anime),
            freeSpace = emptyMap(),
            rootFolders = emptyList(),
            rootFolder = server.defaultDirectory(anime),
            tags = emptyList(),
            tagIds = emptySet(),
            loadingChoices = true,
        )

    /**
     * The chosen instance's own lists, with the current pick kept where the instance still offers
     * it. Where it does not, the instance's first is taken — and where the instance lists none at
     * all, the pick stands: an empty list is an instance that did not say, not one that said no.
     */
    fun withChoices(details: SeerrServerDetailsDto): DestinationChoices =
        copy(
            profiles = details.profiles.map { Choice(it.id, it.name) },
            profileId = details.profiles.firstOrNull { it.id == profileId }?.id ?: details.profiles.firstOrNull()?.id ?: profileId,
            rootFolders = details.rootFolders.map { it.path },
            rootFolder =
                details.rootFolders.firstOrNull { it.path == rootFolder }?.path ?: details.rootFolders.firstOrNull()?.path ?: rootFolder,
            tags = details.tags.map { Choice(it.id, it.label) },
            tagIds = tagIds.filter { id -> details.tags.any { it.id == id } }.toSet(),
            defaultProfileId = details.server?.defaultProfile(anime) ?: defaultProfileId,
            freeSpace = details.rootFolders.mapNotNull { folder -> folder.freeSpace?.let { folder.path to it } }.toMap(),
            loadingChoices = false,
        )
}

/**
 * The destination fields' glyphs, so the request-with-options page and the request editor show the same three. They
 * match the request card's own server, quality and folder facts.
 */
internal object DestinationIcons {
    val server: ImageVector = Icons.Filled.Dns
    val profile: ImageVector = Icons.Filled.HighQuality
    val rootFolder: ImageVector = Icons.Filled.Folder
    val tags: ImageVector = Icons.Filled.LocalOffer
}

private fun SeerrServerDto.defaultProfile(anime: Boolean) = if (anime) activeAnimeProfileId ?: activeProfileId else activeProfileId

private fun SeerrServerDto.defaultDirectory(anime: Boolean) = if (anime) activeAnimeDirectory ?: activeDirectory else activeDirectory

private fun SeerrServerRefDto.defaultProfile(anime: Boolean) = if (anime) activeAnimeProfileId ?: activeProfileId else activeProfileId
