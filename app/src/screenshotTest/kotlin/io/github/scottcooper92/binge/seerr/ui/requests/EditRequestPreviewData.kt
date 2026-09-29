package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.DestinationChoices

private const val LONG_RUN_SEASONS = 24
private const val HELD_SEASONS = 3
private const val FIRST_TICKED_SEASON = 10
private const val LAST_TICKED_SEASON = 12
private const val SHORT_RUN_SEASONS = 5
private const val EPISODES_BASE = 10
private const val EPISODES_SPREAD = 5
private const val SONARR_ID = 1
private const val PROFILE_ID = 4

/** A long run: the first seasons already on the server, a few ticked, and the rest left to choose. */
internal fun longRunEditState(): EditState =
    EditState(
        seasons =
            (1..LONG_RUN_SEASONS).map { number ->
                SeasonChoice(
                    number = number,
                    name = null,
                    episodeCount = EPISODES_BASE + number % EPISODES_SPREAD,
                    selected = number in FIRST_TICKED_SEASON..LAST_TICKED_SEASON,
                    heldStatus = if (number <= HELD_SEASONS) SeerrMediaStatusCode.Available else null,
                )
            },
        destination = editDestination(),
    )

/** Every season the editor may change is ticked, so the bulk toggle offers to clear rather than select. */
internal fun allTickedEditState(): EditState =
    EditState(
        seasons =
            (1..SHORT_RUN_SEASONS).map { number ->
                SeasonChoice(
                    number = number,
                    name = null,
                    episodeCount = EPISODES_BASE,
                    selected = true,
                    heldStatus = if (number == 1) SeerrMediaStatusCode.Available else null,
                )
            },
        destination = editDestination(),
    )

private fun editDestination() =
    DestinationChoices(
        servers = listOf(Choice(SONARR_ID, "Sonarr")),
        serverId = SONARR_ID,
        profiles = listOf(Choice(PROFILE_ID, "HD-1080p")),
        profileId = PROFILE_ID,
        rootFolders = listOf("/tv"),
        rootFolder = "/tv",
    )
