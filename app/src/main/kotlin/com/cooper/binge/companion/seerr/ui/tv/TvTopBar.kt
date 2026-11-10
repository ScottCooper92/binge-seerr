package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.nav.BingeTvTopBar
import com.binge.designsystem.tv.nav.TvAccountMenu
import com.binge.designsystem.tv.nav.TvAccountMenuRow
import com.binge.designsystem.tv.nav.TvNavItem
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/**
 * Who is signed in, for the avatar at the bar's end and the menu that opens from it. [detail] is the line under the
 * name; [extraContent] sits under the header (the quota, say) and is composed only while the menu is open; [rows] are
 * the menu's actions. [initiallyOpen] draws the menu open until focus first reaches it, for a screenshot.
 */
internal class TvTopBarAccount(
    val name: String,
    val avatarUrl: String?,
    /** Whether the connected server is the demo (#1705), which the bar says so beside the avatar. */
    val demo: Boolean = false,
    val detail: String? = null,
    val rows: List<TvAccountMenuRow> = emptyList(),
    val extraContent: (@Composable ColumnScope.() -> Unit)? = null,
    val initiallyOpen: Boolean = false,
)

/**
 * The shell's bar, which the design system draws ([BingeTvTopBar]): [tabs] are its items, and the trailing slot holds the demo
 * tag, the account menu ([TvAccountMenu]) and the Settings gear. The shell reaches each through its requester ([opener] for a tab
 * and the gear, [accountOpener] for the avatar), and Down from any of them lands on [down], the current page's own entry.
 *
 * Until [visible] (the server has said which tabs this account gets) the bar is drawn clear and takes no focus.
 */
@Composable
internal fun TvTopBar(
    tabs: List<TvDestination>,
    current: TvDestination,
    opener: Map<TvDestination, FocusRequester>,
    accountOpener: FocusRequester,
    down: FocusRequester,
    visible: Boolean,
    account: TvTopBarAccount,
    onFocusedChange: (Boolean) -> Unit,
    onSelect: (TvDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = tabs.associateWith { stringResource(it.labelRes) }
    val items = tabs.map { TvNavItem(key = it, label = labels.getValue(it), icon = it.icon) }
    val settingsLabel = stringResource(TvDestination.Settings.labelRes)
    // Down from the gear is the caller's to name, as the bar's own tabs get it.
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
            TvAccountMenu(
                avatarLabel = stringResource(R.string.tv_account),
                name = account.name,
                avatarUrl = account.avatarUrl,
                detail = account.detail,
                rows = account.rows,
                down = down,
                modifier = Modifier.focusRequester(accountOpener),
                extraContent = account.extraContent,
                initiallyOpen = account.initiallyOpen,
            )
            TvIconButton(
                icon = TvDestination.Settings.icon,
                label = settingsLabel,
                onClick = onOpenSettings,
                modifier = below.focusRequester(opener.getValue(TvDestination.Settings)),
                // Stays a circle on focus: the bar lays the tabs out around this slot, so it must not change size.
                expandOnFocus = false,
            )
        },
    )
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
