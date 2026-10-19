package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.LocalReduceMotion
import com.binge.designsystem.tv.component.BingeTvInitialsAvatar
import com.binge.designsystem.tv.focus.tvClickable
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** The tabs at rest, while focus is on the page: smaller, so the bar recedes behind what the user is reading. */
private const val TAB_RESTING_SCALE = 0.8f
private const val TAB_SCALE_MILLIS = 150

/** The current tab's pill while focus is elsewhere: there, but quieter than a focused item's. */
private const val CURRENT_TAB_ALPHA = 0.2f

/** The scrim under the bar, from nearly opaque at the top edge to clear, so a page's art shows through below it. */
private const val BAR_SCRIM_TOP_ALPHA = 0.9f

/** Who is signed in, for the avatar at the bar's start. */
internal class TvTopBarAccount(
    val name: String,
    val avatarUrl: String?,
    /** Whether the connected server is the demo (#1705), which the bar says so beside Settings. */
    val demo: Boolean = false,
)

/**
 * The bar over every page: the signed-in user's avatar at the start, the tabs centred, and Settings as a gear at the end.
 * A focused item is a light pill with dark text, and the [current] tab keeps a faint pill while focus is elsewhere. Only the
 * tabs scale, from [TAB_RESTING_SCALE] at rest to full size while the bar holds focus, so the two ends stay pinned.
 *
 * Until [visible] (the server has said which tabs this account gets) the bar is drawn clear and takes no focus, so it never
 * shows a tab that is about to vanish. [down] is where Down from any item lands: the current page's own entry.
 */
@Composable
internal fun TvTopBar(
    destinations: List<TvDestination>,
    current: TvDestination,
    opener: Map<TvDestination, FocusRequester>,
    down: FocusRequester,
    visible: Boolean,
    account: TvTopBarAccount,
    onFocusedChange: (Boolean) -> Unit,
    onSelect: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var barFocused by remember { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.background
    val scale by animateFloatAsState(
        targetValue = if (barFocused) 1f else TAB_RESTING_SCALE,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(TAB_SCALE_MILLIS),
        label = "tvTopBarTabScale",
    )
    val item = { destination: TvDestination ->
        Modifier.focusRequester(opener.getValue(destination)).focusProperties {
            canFocus = visible
            this.down = down
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (visible) 1f else 0f)
                .onFocusChanged {
                    barFocused = it.hasFocus
                    onFocusedChange(it.hasFocus)
                }.background(Brush.verticalGradient(listOf(background.copy(alpha = BAR_SCRIM_TOP_ALPHA), Color.Transparent)))
                .padding(
                    top = dimensionResource(TvR.dimen.tv_overscan_vertical),
                    bottom = dimensionResource(DesR.dimen.padding_m),
                    start = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                    end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                ),
    ) {
        if (TvDestination.Account in destinations) {
            TvTopBarAvatar(
                label = stringResource(TvDestination.Account.labelRes),
                account = account,
                onClick = { onSelect(TvDestination.Account) },
                modifier = item(TvDestination.Account).align(Alignment.CenterStart),
            )
        }
        Row(
            modifier =
                Modifier.align(Alignment.Center).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.filterNot { it.isSheet }.forEach { destination ->
                TvTopBarItem(
                    label = stringResource(destination.labelRes),
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    modifier = item(destination),
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (account.demo) TvDemoTag()
            if (TvDestination.Settings in destinations) {
                TvTopBarItem(
                    label = stringResource(TvDestination.Settings.labelRes),
                    selected = false,
                    onClick = { onSelect(TvDestination.Settings) },
                    modifier = item(TvDestination.Settings),
                    icon = TvDestination.Settings.icon,
                )
            }
        }
    }
}

/** The signed-in user's picture, or their initials, ringed in the focused pill's colour when focused. */
@Composable
private fun TvTopBarAvatar(
    label: String,
    account: TvTopBarAccount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val diameter = dimensionResource(TvR.dimen.tv_icon_button_diameter)
    val ring = dimensionResource(R.dimen.tv_top_bar_avatar_ring)
    Box(
        modifier =
            modifier
                .size(diameter)
                .clip(BingeShapes.Pill)
                .border(ring, if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent, BingeShapes.Pill)
                .tvClickable(role = Role.Button, onFocusChanged = { focused = it }, onClick = onClick)
                .clearAndSetSemantics { contentDescription = label }
                .padding(ring),
        contentAlignment = Alignment.Center,
    ) {
        BingeTvInitialsAvatar(name = account.name, size = diameter - ring * 2, avatarUrl = account.avatarUrl)
    }
}

/** A tab as a text pill, or, given an [icon], as that icon alone; named for a screen reader either way. */
@Composable
private fun TvTopBarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val container =
        when {
            focused -> scheme.onSurface
            selected -> scheme.onSurface.copy(alpha = CURRENT_TAB_ALPHA)
            else -> Color.Transparent
        }
    val content = if (focused) scheme.surface else scheme.onSurface
    Row(
        modifier =
            modifier
                .heightIn(min = dimensionResource(TvR.dimen.tv_icon_button_diameter))
                .clip(BingeShapes.Pill)
                .background(container)
                .tvClickable(role = Role.Button, onFocusChanged = { focused = it }, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = label
                    this.selected = selected
                }.padding(horizontal = dimensionResource(R.dimen.tv_top_bar_item_padding_horizontal)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(dimensionResource(TvR.dimen.tv_button_icon)),
            )
        } else {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = content)
        }
    }
}

/** The demo server's mark: small, and not a control, so the remote passes over it. */
@Composable
private fun TvDemoTag() {
    Text(
        text = stringResource(R.string.demo_indicator),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .clip(BingeShapes.Pill)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = DEMO_TAG_ALPHA))
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_xs)),
    )
}

private const val DEMO_TAG_ALPHA = 0.12f
