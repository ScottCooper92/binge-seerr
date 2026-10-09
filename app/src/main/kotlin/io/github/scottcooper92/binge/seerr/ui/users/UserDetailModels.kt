package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuota
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/** Seerr's server owner is always user 1: never deletable, and the only one who may delete another admin. */
const val OWNER_USER_ID = 1

/** A title on a carousel, hydrated through the title cache; the card opens it on the server. */
data class TitleCardItem(
    val tmdbId: Int,
    val mediaType: RequestMediaType,
    val title: String?,
    val posterUrl: String?,
)

/** Tautulli's plays for the user, and what they watched last; absent where the server has none. */
data class UserWatch(
    val playCount: Int?,
    val recentlyWatched: List<TitleCardItem>,
)

/**
 * One user as a page: who they are, what they may do, their quota, and the sections the server
 * returned. The delete guard mirrors the server's: `MANAGE_USERS`, never the owner or oneself, and
 * another admin only by the owner.
 */
data class UserDetail(
    val item: UserItem,
    val permissions: Set<ManageablePermission>,
    val quota: HubQuota?,
    val watch: UserWatch?,
    val watchlist: List<TitleCardItem>,
    val isSelf: Boolean,
    /** The server opens a user's settings to the user and to a manager. */
    val canEditSettings: Boolean,
    val canDelete: Boolean,
    /** The server's own root, for a carousel card that opens a title there. */
    val serverUrl: String,
    /** The user in the server's web client. */
    val webUrl: String,
    /** What the server calls itself: Overseerr, Jellyseerr or Seerr. */
    val serverName: String = "Seerr",
    /** The server lists a user's requests to that user and to a viewer who may see everyone's; the section is hidden otherwise. */
    val canViewRequests: Boolean = true,
)

sealed interface UserDetailUiState {
    data object Loading : UserDetailUiState

    /**
     * The row the user tapped, shown while the profile loads: the header, the role and origin tags,
     * the permissions and the request count are all on it, but the quota, the watch data and what the
     * viewer may do are not, so none of those are guessed. A failed refresh sets [error] and leaves the
     * profile up.
     */
    data class Seeded(
        val item: UserItem,
        val error: SeerrError? = null,
    ) : UserDetailUiState

    data class Ready(
        val detail: UserDetail,
        val deleting: Boolean = false,
    ) : UserDetailUiState

    data class Error(
        val error: SeerrError,
    ) : UserDetailUiState
}

sealed interface UserDetailEvent {
    /** The user is gone, so the page pops rather than reporting over a surface it is leaving. */
    data object UserDeleted : UserDetailEvent

    data class Failed(
        val error: SeerrError,
    ) : UserDetailEvent
}
