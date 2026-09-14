package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDto

/**
 * Where a request goes: the instances it may go to, and the chosen one's profiles, root folders and
 * tags. Shared by the advanced-options hand-off and the request editor, which sequence the two
 * fetches the same way and differ only in what they do with a failure.
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
            profileId = server.activeProfileId,
            rootFolders = emptyList(),
            rootFolder = server.activeDirectory,
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
            loadingChoices = false,
        )
}
