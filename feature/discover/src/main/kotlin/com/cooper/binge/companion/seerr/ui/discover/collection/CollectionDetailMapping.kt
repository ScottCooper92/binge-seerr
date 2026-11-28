package com.cooper.binge.companion.seerr.ui.discover.collection

import android.content.res.Resources
import com.binge.designsystem.component.MediaResult
import com.binge.designsystem.component.MediaResultKind
import com.binge.designsystem.template.CollectionDetail
import com.binge.designsystem.template.CollectionDetailState
import com.binge.designsystem.template.ScreenAction
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.titleRes
import com.cooper.binge.companion.seerr.ui.state.toErrorKind

/**
 * The collection page's state as the shared template takes it: plain values, with every word already in the user's language.
 * [allowLocalNetwork] is the action a blocked local server needs; Try again cannot fix that, so it replaces it.
 */
fun CollectionPageUiState.toDetailState(
    resources: Resources,
    allowLocalNetwork: ScreenAction? = null,
): CollectionDetailState =
    when (this) {
        CollectionPageUiState.Loading -> CollectionDetailState.Loading()
        is CollectionPageUiState.Error ->
            CollectionDetailState.Failed(
                kind = error.toErrorKind(),
                title = resources.getString(error.titleRes()),
                message = resources.getString(error.messageRes()),
                retryable = !(error == SeerrError.LocalNetworkDenied && allowLocalNetwork != null),
                secondary = allowLocalNetwork.takeIf { error == SeerrError.LocalNetworkDenied },
            )
        is CollectionPageUiState.Ready -> CollectionDetailState.Content(collection.toDetail(mayRequest, resources))
    }

private fun CollectionDetails.toDetail(
    mayRequest: Boolean,
    resources: Resources,
): CollectionDetail {
    val films = resources.getQuantityString(R.plurals.collection_page_films, parts.size, parts.size)
    return CollectionDetail(
        title = name,
        backdropUrl = backdropUrl,
        metaText = if (mayRequest && toRequest > 0) resources.getString(R.string.collection_page_meta, films, toRequest) else films,
        overview = overview,
        overviewHeading = resources.getString(R.string.title_page_overview),
        partsHeading = resources.getString(R.string.collection_page_parts_heading),
        parts = parts.map { it.toResult() },
        emptyMessage = resources.getString(R.string.collection_page_empty),
    )
}

/** The rating and the year go in the row's own fields, so they follow the status chip and wrap with it. */
fun TitleResult.toResult() =
    MediaResult(
        key = partKey(tmdbId),
        kind = MediaResultKind.Poster,
        imageUrl = artworkPath?.toTmdbPosterUrl(),
        name = name,
        rating = rating?.takeIf { it > 0.0 }?.toFloat(),
        meta = year,
        blurb = overview?.takeIf { it.isNotBlank() },
    )

fun partKey(tmdbId: Int) = tmdbId.toString()
