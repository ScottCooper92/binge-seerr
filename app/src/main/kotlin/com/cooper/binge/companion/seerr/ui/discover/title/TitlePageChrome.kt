package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.DetailRoundAction
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.theme.BingeTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.actionItem
import com.cooper.binge.companion.seerr.ui.state.externalItem
import com.binge.designsystem.R as DesR

/** The most round buttons a title page shows beside its primary. */
internal const val ROUND_SLOTS = 4

/** The title's secondary actions that have no fixed round button, in the order they take a free one. */
internal class TitleSpill(
    val onTrailer: (() -> Unit)?,
    val onReport: (() -> Unit)?,
    val onShare: () -> Unit,
    val onBlock: (() -> Unit)?,
)

/** Where [TitleSpill]'s actions went: some into free round buttons, Report into the bar if it got none, and the rest into More. */
internal class TitleChromePlacement(
    val roundTrailer: Boolean,
    val roundReport: Boolean,
    val roundShare: Boolean,
    val roundBlock: Boolean,
) {
    fun barReport(spill: TitleSpill) = spill.onReport.takeUnless { roundReport }

    fun moreTrailer(spill: TitleSpill) = spill.onTrailer.takeUnless { roundTrailer }

    fun moreShare(spill: TitleSpill) = spill.onShare.takeUnless { roundShare }

    fun moreBlock(spill: TitleSpill) = spill.onBlock.takeUnless { roundBlock }

    fun hasMore(spill: TitleSpill) = moreTrailer(spill) != null || moreShare(spill) != null || moreBlock(spill) != null
}

/** Fills the round buttons left after [fixedRound] with Trailer, Report, Share then Block, each where the title has it. */
internal fun placeSpill(
    fixedRound: Int,
    spill: TitleSpill,
): TitleChromePlacement {
    var free = (ROUND_SLOTS - fixedRound).coerceAtLeast(0)

    fun take(present: Boolean): Boolean = (present && free > 0).also { if (it) free-- }
    return TitleChromePlacement(
        roundTrailer = take(spill.onTrailer != null),
        roundReport = take(spill.onReport != null),
        roundShare = take(true),
        roundBlock = take(spill.onBlock != null),
    )
}

/** The round buttons [placement] gave to [spill]'s actions, after the fixed ones. */
@Composable
internal fun spillRoundActions(
    spill: TitleSpill,
    placement: TitleChromePlacement,
): List<DetailRoundAction> {
    val trailerLabel = stringResource(R.string.title_page_action_trailer)
    val trailerDescription = stringResource(DesR.string.cd_link_chip_opens_outside, trailerLabel)
    val reportLabel = stringResource(R.string.title_page_action_report)
    val shareLabel = stringResource(R.string.title_page_action_share)
    val blockLabel = stringResource(R.string.title_page_action_block)
    return listOfNotNull(
        spill.onTrailer?.takeIf { placement.roundTrailer }?.let {
            DetailRoundAction(
                Icons.Filled.Theaters,
                trailerLabel,
                it,
                trailerDescription,
            )
        },
        spill.onReport?.takeIf { placement.roundReport }?.let { DetailRoundAction(Icons.Filled.ReportProblem, reportLabel, it) },
        spill.onShare.takeIf { placement.roundShare }?.let { DetailRoundAction(Icons.Filled.Share, shareLabel, it) },
        spill.onBlock?.takeIf { placement.roundBlock }?.let { DetailRoundAction(Icons.Filled.Block, blockLabel, it) },
    )
}

/** The bar's actions: Report where it got no round button, then More where anything is left for it. */
@Composable
internal fun TitleTopBarActions(
    glassBackgroundAlpha: Float,
    onReport: (() -> Unit)?,
    onMore: (() -> Unit)?,
) {
    Row {
        onReport?.let { BarButton(Icons.Filled.ReportProblem, stringResource(R.string.issue_report_title), glassBackgroundAlpha, it) }
        onMore?.let { BarButton(Icons.Filled.MoreVert, stringResource(R.string.title_page_action_more), glassBackgroundAlpha, it) }
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    description: String,
    glassBackgroundAlpha: Float,
    onClick: () -> Unit,
) {
    ExpressiveIconButton(
        onClick = onClick,
        icon = icon,
        contentDescription = description,
        tint = lerp(BingeTheme.colors.onScrim, MaterialTheme.colorScheme.onBackground, 1f - glassBackgroundAlpha),
        tone = IconButtonTone.Glass,
        size = dimensionResource(DesR.dimen.top_bar_icon_size),
        glassBackgroundAlpha = glassBackgroundAlpha,
    )
}

/** What did not fit in the round buttons: Trailer, Share and Block, each where it is left over. A tap closes the sheet first. */
@Composable
internal fun TitleMoreSheet(
    onTrailer: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onBlock: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val close: (() -> Unit) -> () -> Unit = { action ->
        {
            onDismiss()
            action()
        }
    }
    BingeAdaptiveSheet(kind = BingeSheetKind.Menu, onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        ) {
            ItemGroup(
                title = null,
                rows =
                    listOfNotNull(
                        onTrailer?.let {
                            externalItem(
                                Icons.Filled.Theaters,
                                stringResource(R.string.title_page_action_trailer),
                                close(it),
                            )
                        },
                        onShare?.let {
                            actionItem(
                                Icons.Filled.Share,
                                stringResource(R.string.title_page_action_share),
                                onClick = close(it),
                            )
                        },
                        onBlock?.let {
                            actionItem(
                                Icons.Filled.Block,
                                stringResource(R.string.title_page_action_block),
                                destructive = true,
                                onClick = close(it),
                            )
                        },
                    ),
            )
        }
    }
}
