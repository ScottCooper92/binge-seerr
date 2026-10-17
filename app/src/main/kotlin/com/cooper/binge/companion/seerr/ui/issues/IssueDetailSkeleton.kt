package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.CARD_ASPECT_RATIO
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.ui.state.SectionHeaderSkeleton
import com.cooper.binge.companion.seerr.ui.state.StatusChipSkeleton
import com.binge.designsystem.R as DesR

private const val TITLE_FRACTION = 0.6f
private const val META_FRACTION = 0.4f
private const val TYPE_LABEL_FRACTION = 0.25f
private const val REPORT_LINE_COUNT = 2
private const val REPORT_LAST_LINE_FRACTION = 0.6f

/**
 * Loading placeholder for [IssueDetailScreen]'s [IssueDetailUiState.Ready] arm: the [IssueItem] header, the
 * divider, the problem report and the Comments heading. Every issue has a report ([IssueDetail.report], its
 * opening comment), so two lines are held for it and the heading does not jump on resolve (#572). The link
 * buttons and the thread's comments depend on the server's data, so nothing is reserved for them, the same
 * trade [RequestDetailSkeleton] and [UserDetailSkeleton] make (#373). The pinned composer/resolve bar is
 * skipped too: its shape varies with permissions, so a guessed one would cause reflow rather than avoid it.
 */
@Composable
internal fun IssueDetailSkeleton(modifier: Modifier = Modifier) {
    val inset = resolvedContentInset()
    val sides = resolvedContentPadding()
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderSkeleton(modifier = Modifier.padding(resolvedContentPadding(vertical = inset)))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(sides))
        IssueBodySkeleton()
    }
}

/**
 * What sits under the header and divider while the issue loads: the problem report and the Comments
 * heading. Shared with the seeded page, which shows the real header above it, so both resolve the same way.
 */
@Composable
internal fun IssueBodySkeleton() {
    ReportSkeleton(modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)))
    SectionHeaderSkeleton()
}

/** The report section: its Problem heading and two `bodyLarge` lines at the content inset, as the page sets the report's text. */
@Composable
private fun ReportSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderSkeleton()
        repeat(REPORT_LINE_COUNT) { index ->
            SkeletonPlate(
                Modifier
                    .padding(resolvedContentPadding())
                    .fillMaxWidth(if (index == REPORT_LINE_COUNT - 1) REPORT_LAST_LINE_FRACTION else 1f)
                    .height(lineHeightOf(MaterialTheme.typography.bodyLarge)),
            )
        }
    }
}

/** Mirrors [IssueHeader]'s poster-leading row: the poster, title, meta line, and type/status row. */
@Composable
private fun HeaderSkeleton(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SkeletonPlate(
            Modifier
                .width(dimensionResource(DesR.dimen.list_row_poster_width))
                .aspectRatio(CARD_ASPECT_RATIO),
            shape = BingeShapes.ListRowPoster,
        )
        Column(
            modifier = Modifier.padding(start = dimensionResource(DesR.dimen.list_row_gap)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)),
        ) {
            SkeletonPlate(
                Modifier.fillMaxWidth(TITLE_FRACTION).height(lineHeightOf(MaterialTheme.typography.titleMedium)),
            )
            SkeletonPlate(
                Modifier.fillMaxWidth(META_FRACTION).height(lineHeightOf(MaterialTheme.typography.bodyMedium)),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonPlate(
                    Modifier.fillMaxWidth(TYPE_LABEL_FRACTION).height(lineHeightOf(MaterialTheme.typography.labelMedium)),
                )
                StatusChipSkeleton(dimensionResource(DesR.dimen.info_row_label_min_width))
            }
        }
    }
}
