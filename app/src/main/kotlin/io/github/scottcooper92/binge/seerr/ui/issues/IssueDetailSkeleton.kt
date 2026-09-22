package io.github.scottcooper92.binge.seerr.ui.issues

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
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.labelSmallEmphasis
import io.github.scottcooper92.binge.seerr.ui.state.SectionHeaderSkeleton
import io.github.scottcooper92.binge.seerr.ui.state.SkeletonPlate
import com.binge.designsystem.R as DesR

private const val TITLE_FRACTION = 0.6f
private const val META_FRACTION = 0.4f
private const val TYPE_LABEL_FRACTION = 0.25f

/**
 * Loading placeholder for [IssueDetailScreen]'s [IssueDetailUiState.Ready] arm: the issue header —
 * poster, title, the media-type/year meta line, and the type label plus status chip row — all
 * unconditional on [IssueItem], then the divider and the Comments section header every issue has.
 *
 * The problem report, the media-server/service link buttons and the thread's own comments are all
 * conditional on the server's own data ([IssueDetail.report] is nullable, [IssueDetail.mediaServerUrl]/
 * [IssueDetail.serviceUrl] likewise, and the thread's length is whatever the server returns), so
 * nothing is reserved for them — the scroll grows on resolve rather than reflowing a guess, the same
 * trade [RequestDetailSkeleton] and [UserDetailSkeleton] make (#373).
 *
 * The pinned composer/resolve bar is skipped for a different reason than either of those two skip
 * their own conditional content: its shape itself varies with permissions (a composer alone, a
 * resolve button alone, both, or the bar absent entirely), so a guessed shape would be its own
 * source of reflow rather than avoiding one — unlike [RequestDetailSkeleton]'s single button, which
 * only ever varies in whether it is reserved, never in what it looks like.
 */
@Composable
internal fun IssueDetailSkeleton(modifier: Modifier = Modifier) {
    val inset = resolvedContentInset()
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderSkeleton(modifier = Modifier.padding(inset))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = inset))
        SectionHeaderSkeleton()
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
                ChipSkeleton()
            }
        }
    }
}

/** [com.binge.designsystem.component.BingeTag]'s own height and corner — the status chip's shape. */
@Composable
private fun ChipSkeleton(modifier: Modifier = Modifier) {
    SkeletonPlate(
        modifier
            .width(dimensionResource(DesR.dimen.info_row_label_min_width))
            .height(lineHeightOf(MaterialTheme.typography.labelSmallEmphasis) + dimensionResource(DesR.dimen.tag_padding_v) * 2),
        shape = BingeShapes.Tag,
    )
}
