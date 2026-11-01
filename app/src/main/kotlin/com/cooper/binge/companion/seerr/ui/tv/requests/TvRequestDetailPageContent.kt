package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.component.TV_DETAILS_SECTION_KEY
import com.binge.designsystem.tv.component.TvCardRow
import com.binge.designsystem.tv.component.TvDetailAction
import com.binge.designsystem.tv.component.TvDetailActionRow
import com.binge.designsystem.tv.component.TvDetailHero
import com.binge.designsystem.tv.component.TvDetailHeroItem
import com.binge.designsystem.tv.component.TvDetailInfoFooterSection
import com.binge.designsystem.tv.component.TvHeroOverview
import com.binge.designsystem.tv.component.TvInfoGroup
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.template.TvDetailPageScope
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.cooper.binge.companion.seerr.ui.requests.statusChip
import com.cooper.binge.companion.seerr.ui.tv.TvActionRun
import com.cooper.binge.companion.seerr.ui.tv.TvBackdropArtwork

/** The hero band: poster, the title over its state, and the synopsis when there is no action to take focus first. */
@Composable
internal fun RequestHero(
    item: RequestItem,
    now: Long,
    actionList: List<TvDetailAction>,
    actionRowFocus: FocusRequester,
    synopsisFocus: FocusRequester,
    synopsisFocused: Boolean,
    onSynopsisFocused: (Boolean) -> Unit,
    run: TvActionRun?,
) {
    val separator = stringResource(R.string.hub_meta_separator)
    val chip = item.statusChip()
    val title = item.title ?: stringResource(item.mediaType.labelRes())
    TvDetailHero(
        item =
            TvDetailHeroItem(
                title = title,
                overline =
                    listOfNotNull(
                        stringResource(item.mediaType.labelRes()),
                        stringResource(R.string.settings_service_4k).takeIf { item.is4k },
                    ).joinToString(separator),
                overview = item.overview,
                facts =
                    listOfNotNull(
                        item.year,
                        stringResource(chip.labelRes),
                        item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                        formatRelativeOrAbsolute(item.requestedAtMillis, now),
                    ),
                certification = item.certification,
            ),
        artwork = { TvBackdropArtwork(item.backdropUrl, item.posterUrl) },
        poster = {
            item.posterUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        overview =
            if (actionList.isEmpty()) {
                TvHeroOverview(
                    isFocused = synopsisFocused,
                    onFocusChanged = onSynopsisFocused,
                    onClick = {},
                    focusRequester = synopsisFocus,
                )
            } else {
                null
            },
    ) {
        TvDetailActionRow(actions = actionList, entryFocus = actionRowFocus)
        run?.let { TvSaveStatus(status = it.status, onRetry = it::again) }
    }
}

/** The rows below the hero: the seasons asked for and what is downloading, then the request's details. */
internal fun TvDetailPageScope.requestSections(
    detail: RequestDetail,
    details: TvRequestDetails,
) {
    if (detail.seasons.isNotEmpty()) {
        section("seasons") { onFocused ->
            TvCardRow(
                items = detail.seasons,
                key = { it.number },
                cellWidth = dimensionResource(R.dimen.tv_detail_season_card_width),
                heading = stringResource(R.string.request_seasons),
                onCellFocused = onFocused,
            ) { season, isFocused, onFocusChanged, cellModifier ->
                TvSeasonCard(season, isFocused, onFocusChanged, cellModifier)
            }
        }
    }
    if (detail.downloads.isNotEmpty()) {
        section("downloads") { onFocused ->
            TvCardRow(
                items = detail.downloads.mapIndexed { index, download -> index to download },
                key = { it.first },
                cellWidth = dimensionResource(R.dimen.tv_detail_download_card_width),
                heading = stringResource(R.string.request_downloads),
                onCellFocused = onFocused,
            ) { (_, download), isFocused, onFocusChanged, cellModifier ->
                TvDownloadCard(download, isFocused, onFocusChanged, cellModifier)
            }
        }
    }
    // The page's facts in Binge's own Details footer.
    section(TV_DETAILS_SECTION_KEY) { _ ->
        TvDetailInfoFooterSection(header = details.header, groups = details.groups)
    }
}

/** The Details section's content, resolved where resources can be read: the section builder is not composable. */
internal class TvRequestDetails(
    val header: String,
    val groups: List<TvInfoGroup>,
)
