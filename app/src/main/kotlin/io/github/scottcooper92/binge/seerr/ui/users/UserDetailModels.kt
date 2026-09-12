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
    val canDelete: Boolean,
    /** The server's own root, for a carousel card that opens a title there. */
    val serverUrl: String,
    /** The user in the server's web client. */
    val webUrl: String,
)

sealed interface UserDetailUiState {
    data object Loading : UserDetailUiState

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
