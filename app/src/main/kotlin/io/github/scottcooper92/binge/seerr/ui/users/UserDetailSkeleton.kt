package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.ListRowSkeleton
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.labelSmallEmphasis
import io.github.scottcooper92.binge.seerr.ui.state.SectionHeaderSkeleton
import com.binge.designsystem.R as DesR

private const val NAME_FRACTION = 0.6f
private const val META_FRACTION = 0.8f
private const val META_LINE_COUNT = 2
private const val TAG_COUNT = 2
private const val REQUEST_ROW_COUNT = 3

/**
 * Loading placeholder for [UserDetailScreen]'s [UserDetailUiState.Ready] arm: the profile (avatar,
 * name, two meta lines, the role/origin tags every user has), the Requests section header, and a few
 * request-row plates standing in for the paged list. Everything else on the page is conditional on
 * what the server and the user's own history return, so nothing is reserved for it: the stat row,
 * whose one cell is a play count shown only once the server reports watch data, as well as quota,
 * permissions and the two title carousels. It is the same "reserve only what's unconditional" trade
 * [RequestDetailSkeleton] makes, and the one Binge's own `DetailScreenSkeleton` makes for its
 * details band (#373). A user with watch data gets the stat row on resolve, and what sits below it
 * moves down by its height (#687).
 */
@Composable
internal fun UserDetailSkeleton(modifier: Modifier = Modifier) {
    val inset = resolvedContentInset()
    val sides = resolvedContentPadding()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        // UserDetailContent's LazyColumn spaces every top-level item this way, request rows
        // included — unmatched, the section header and rows would sit closer than the resolved page.
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        ProfileSkeleton(
            modifier =
                Modifier
                    .padding(
                        resolvedContentPadding(vertical = inset),
                    ).layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE)),
        )
        SectionHeaderSkeleton()
        repeat(REQUEST_ROW_COUNT) {
            ListRowSkeleton(
                modifier = Modifier.padding(sides).padding(bottom = dimensionResource(DesR.dimen.list_row_spacing)),
            )
        }
    }
}

/**
 * Mirrors [ProfileHeader]: a row with the avatar beside a column of the name, two meta lines and
 * the two tags (role, origin) every user has. The second meta line is an email distinct from the
 * handle, which a user imported from Plex, Jellyfin or Emby typically has, the common shape on a
 * server backed by one. A local-only user has one line, so what follows the profile rises by that
 * line on resolve: the smaller jump, on the rarer page (#572).
 */
@Composable
private fun ProfileSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        SkeletonPlate(
            Modifier.size(dimensionResource(DesR.dimen.avatar_size_lg)),
            shape = BingeShapes.Pill,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
        ) {
            SkeletonPlate(
                Modifier.fillMaxWidth(NAME_FRACTION).height(lineHeightOf(MaterialTheme.typography.titleLarge)),
            )
            repeat(META_LINE_COUNT) {
                SkeletonPlate(
                    Modifier.fillMaxWidth(META_FRACTION).height(lineHeightOf(MaterialTheme.typography.bodyMedium)),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                repeat(TAG_COUNT) { TagSkeleton() }
            }
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
