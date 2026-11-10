package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.BingeTvInitialsAvatar
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.nav.BingeTvTopBar
import com.binge.designsystem.tv.nav.TvNavItem
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** Who is signed in, for the avatar at the bar's end. */
internal class TvTopBarAccount(
    val name: String,
    val avatarUrl: String?,
    /** Whether the connected server is the demo (#1705), which the bar says so beside Settings. */
    val demo: Boolean = false,
)

/**
 * The shell's bar, which the design system draws ([BingeTvTopBar]): [destinations] without a sheet are the tabs, and the
 * trailing slot holds the demo tag, the signed-in user's avatar and the Settings gear. The shell reaches each through its
 * [opener] requester, and Down from any of them lands on [down], the current page's own entry.
 *
 * Until [visible] (the server has said which tabs this account gets) the bar is drawn clear and takes no focus.
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
    val tabs = destinations.filterNot { it.isSheet }
    val labels = tabs.associateWith { stringResource(it.labelRes) }
    val items = tabs.map { TvNavItem(key = it, label = labels.getValue(it), icon = it.icon) }
    val settingsLabel = stringResource(TvDestination.Settings.labelRes)
    // Down from the avatar and the gear is the caller's to name, as the bar's own tabs get it.
    val below = Modifier.focusProperties { this.down = down }
    BingeTvTopBar(
        header = null,
        items = items,
        footer = null,
        selectedKey = current,
        onSelect = { key -> (key as? TvDestination)?.let(onSelect) },
        itemFocusRequesters = tabs.associate<TvDestination, Any, FocusRequester> { it to opener.getValue(it) },
        down = down,
        onFocusedChange = onFocusedChange,
        modifier = modifier,
        visible = visible,
        trailing = {
            if (account.demo) TvDemoTag()
            if (TvDestination.Account in destinations) {
                TvTopBarAvatar(
                    label = stringResource(TvDestination.Account.labelRes),
                    account = account,
                    onClick = { onSelect(TvDestination.Account) },
                    modifier = below.focusRequester(opener.getValue(TvDestination.Account)),
                )
            }
            if (TvDestination.Settings in destinations) {
                TvIconButton(
                    icon = TvDestination.Settings.icon,
                    label = settingsLabel,
                    onClick = { onSelect(TvDestination.Settings) },
                    modifier = below.focusRequester(opener.getValue(TvDestination.Settings)),
                    // Stays a circle on focus: the bar lays the tabs out around this slot, so it must not change size.
                    expandOnFocus = false,
                )
            }
        },
    )
}

/** The signed-in user's picture, or their initials, ringed in the focused tab's colour when focused. */
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
