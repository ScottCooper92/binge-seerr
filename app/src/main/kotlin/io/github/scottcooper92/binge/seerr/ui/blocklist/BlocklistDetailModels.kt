package io.github.scottcooper92.binge.seerr.ui.blocklist

import io.github.scottcooper92.binge.seerr.seerr.SeerrError

/**
 * One blocked title's page. [item] is known synchronously, off the row that opened it, so the
 * screen is always interactive — this is the flat-state exception `CLAUDE.md`'s Screens section
 * carries a KDoc for, the same shape `LogsUiState` uses: [backdropUrl], [overview] and [webUrl] are
 * the best-effort lookup that arrives after, and pop in once they do rather than gating the page
 * behind a spinner for art the item's own fields already say enough without.
 */
data class BlocklistDetailUiState(
    val item: BlocklistItem,
    val canManage: Boolean,
    val backdropUrl: String? = null,
    val overview: String? = null,
    /** The title on the server's web client; blank until the lookup resolves, so "open elsewhere" waits for it. */
    val webUrl: String = "",
    val unblocking: Boolean = false,
)

sealed interface BlocklistDetailEvent {
    data object Removed : BlocklistDetailEvent

    data class Failed(
        val error: SeerrError,
    ) : BlocklistDetailEvent
}
