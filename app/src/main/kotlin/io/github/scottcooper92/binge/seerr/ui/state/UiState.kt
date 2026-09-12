package io.github.scottcooper92.binge.seerr.ui.state

import io.github.scottcooper92.binge.seerr.seerr.SeerrError

/**
 * A screen's state in three variants, the shape every ported ViewModel is written against: loading,
 * the data (with an in-place refresh flag), or a classified failure. Never a flat class with flags.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<out T>(
        val data: T,
        val isRefreshing: Boolean = false,
    ) : UiState<T>

    data class Error(
        val error: SeerrError,
    ) : UiState<Nothing>
}
