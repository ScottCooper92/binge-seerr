package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.ListRowSkeleton
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.labelSmallEmphasis
import io.github.scottcooper92.binge.seerr.ui.state.SectionHeaderSkeleton
import io.github.scottcooper92.binge.seerr.ui.state.SkeletonPlate
import com.binge.designsystem.R as DesR

private const val NAME_FRACTION = 0.4f
private const val TAG_COUNT = 2
private const val STAT_COUNT = 2
private const val STAT_VALUE_FRACTION = 0.45f
private const val REQUEST_ROW_COUNT = 3

/**
 * Loading placeholder for [UserDetailScreen]'s [UserDetailUiState.Ready] arm: the profile (avatar,
 * name, the role/origin tags every user has), the two stats every user has (request count, joined
 * date — a play count only appears once the server reports watch data), the Requests section header,
 * and a few request-row plates standing in for the paged list. Quota, permissions and the two title
 * carousels are all conditional on what the server and the user's own history return, so nothing is
 * reserved for them — the same "reserve only what's unconditional" trade [RequestDetailSkeleton]
 * makes, and the one Binge's own `DetailScreenSkeleton` makes for its details band (#373).
 */
@Composable
internal fun UserDetailSkeleton(modifier: Modifier = Modifier) {
    val inset = resolvedContentInset()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        // UserDetailContent's LazyColumn spaces every top-level item this way, request rows
        // included — unmatched, the section header and rows would sit closer than the resolved page.
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        ProfileSkeleton(modifier = Modifier.padding(inset).layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE)))
        StatRowSkeleton(modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.STATS)))
        SectionHeaderSkeleton()
        repeat(REQUEST_ROW_COUNT) {
            ListRowSkeleton(
                modifier = Modifier.padding(horizontal = inset).padding(bottom = dimensionResource(DesR.dimen.list_row_spacing)),
            )
        }
    }
}

/** Mirrors [ProfileHeader]: a centred avatar, a name bar, and the two tags (role, origin) every user has. */
@Composable
private fun ProfileSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        SkeletonPlate(
            Modifier.size(dimensionResource(DesR.dimen.avatar_size_lg)),
            shape = BingeShapes.Pill,
        )
        SkeletonPlate(
            Modifier.fillMaxWidth(NAME_FRACTION).height(lineHeightOf(MaterialTheme.typography.titleLarge)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            repeat(TAG_COUNT) { TagSkeleton() }
        }
    }
}

/** [com.binge.designsystem.component.BingeTag]'s own height and corner — the same shape [RequestDetailSkeleton]'s chip skeleton stands in for. */
@Composable
private fun TagSkeleton(modifier: Modifier = Modifier) {
    SkeletonPlate(
        modifier
            .width(dimensionResource(DesR.dimen.info_row_label_min_width))
            .heightIn(min = lineHeightOf(MaterialTheme.typography.labelSmallEmphasis) + dimensionResource(DesR.dimen.tag_padding_v) * 2),
        shape = BingeShapes.Tag,
    )
}

/**
 * Mirrors [DetailStatRow]'s own cell — the icon, the value line and the label line, divided —
 * for the two stats [userStats] always has: request count and joined date. A play count only
 * appears once the server reports watch data, so a third cell isn't reserved.
 */
@Composable
private fun StatRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = resolvedContentInset()).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STAT_COUNT) { index ->
            if (index > 0) {
                VerticalDivider(
                    modifier =
                        Modifier
                            .height(dimensionResource(DesR.dimen.detail_stat_divider_height))
                            .padding(vertical = dimensionResource(DesR.dimen.detail_meta_spacing)),
                )
            }
            StatCellSkeleton(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCellSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = dimensionResource(DesR.dimen.detail_meta_spacing)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)),
    ) {
        SkeletonPlate(Modifier.size(dimensionResource(DesR.dimen.detail_stat_icon_size)))
        SkeletonPlate(Modifier.fillMaxWidth(STAT_VALUE_FRACTION).height(lineHeightOf(MaterialTheme.typography.titleSmall)))
        SkeletonPlate(Modifier.fillMaxWidth(STAT_VALUE_FRACTION).height(lineHeightOf(MaterialTheme.typography.labelSmall)))
    }
}
