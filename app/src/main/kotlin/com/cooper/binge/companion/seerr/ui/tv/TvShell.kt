package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import com.binge.designsystem.theme.LocalReduceMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import com.binge.designsystem.tv.nav.LocalTvTabHosted
import com.binge.designsystem.tv.nav.LocalTvTopInset
import com.binge.designsystem.tv.component.BingeTvInitialsAvatar
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.tvClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.focus.TvFocusSink
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvExitFocusGroup
import com.binge.designsystem.tv.nav.LocalTvHostedAsOverlay
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** The pages of the connected TV app: Home, and the three that open above it from its top row. */
internal enum class TvDestination(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    /** Whether the page is a side sheet over Home, which draws its own scrim, rather than a full-screen page. */
    val isSheet: Boolean = false,
) {
    Account("account", R.string.tv_rail_account, Icons.Filled.Person, isSheet = true),
    Hub("hub", R.string.tv_rail_hub, Icons.Filled.Home),
    Issues("issues", R.string.hub_section_issues, Icons.Filled.ReportProblem),
    Settings("settings", R.string.hub_section_settings, Icons.Filled.Settings, isSheet = true),
}

/**
 * PROTOTYPE: a persistent top bar over every page, treated like the phone's bottom nav. Home and Issues are tabs: OK on a tab
 * switches to it, OK on the current tab pops it to its root, and Back from a tab's root lands on Home, the way out. Account
 * and Settings open as side sheets over whichever tab is showing. [overlay] (a detail page, a see-all grid) belongs to the
 * current tab and sits below the bar; Up out of the top of any page lands on the current tab's pill.
 */
@Composable
internal fun TvHomeShell(
    tab: TvDestination,
    sheet: TvDestination?,
    onSelectTab: (TvDestination) -> Unit,
    onOpenSheet: (TvDestination) -> Unit,
    onCloseSheet: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    /** Whether Issues is offered: only for an account the server lets list issues. */
    showIssues: Boolean = true,
    /** Whether the server has said which tabs this account gets; the bar stays hidden until it has. */
    tabsResolved: Boolean = true,
    accountName: String = "",
    accountAvatarUrl: String? = null,
    /** False while a full-screen task (editing the connection) is up: the bar hides and pages take no inset. */
    showBar: Boolean = true,
    content: @Composable (destination: TvDestination, close: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(Dp(0f)) }
    var barFocused by remember { mutableStateOf(false) }
    val opener = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val sheetCloser = rememberTvOverlayCloser(restoreTo = opener.getValue(sheet ?: tab), onClose = onCloseSheet)
    val homeEntry = remember { FocusRequester() }
    val issuesEntry = remember { FocusRequester() }
    val overlayEntry = remember { FocusRequester() }
    val contentEntry =
        when {
            overlay != null -> overlayEntry
            tab == TvDestination.Issues -> issuesEntry
            else -> homeEntry
        }
    val currentTab = opener.getValue(tab)
    // Up out of a page's top edge goes to the current tab's pill; every other way out of a page is refused.
    val pageTrap =
        Modifier
            .focusProperties {
                onExit = {
                    // Only the D-pad is refused: Back's jump to the bar is a request, not a move, and must get out.
                    when (requestedFocusDirection) {
                        FocusDirection.Up -> currentTab.requestFocus()
                        FocusDirection.Down, FocusDirection.Left, FocusDirection.Right -> cancelFocusChange()
                        else -> Unit
                    }
                }
            }.focusGroup()
    Box(modifier = modifier.fillMaxSize()) {
        // The pages draw under the bar, full-bleed, and pad their content by its height.
        // Back on a tab's page jumps to the bar, as Netflix's does, which saves climbing out of a long list. Back on the
        // bar then leaves Home's tab for Home, or the app from Home.
        BackHandler(enabled = tab == TvDestination.Hub && overlay == null && sheet == null && !barFocused) { currentTab.requestFocus() }
        CompositionLocalProvider(LocalTvTopInset provides if (showBar) barHeight else Dp(0f)) {
            CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
                // Inert while a page covers it, so the page's own hand-off is the only one that takes focus back.
                HandBackHost(
                    entry = homeEntry,
                    active = tab == TvDestination.Hub && sheet == null,
                    modifier = Modifier.focusProperties { onExit = { if (requestedFocusDirection == FocusDirection.Up) currentTab.requestFocus() } },
                ) { content(TvDestination.Hub) { onSelectTab(TvDestination.Hub) } }
            }
            if (tab == TvDestination.Issues) {
                BackHandler(enabled = overlay == null && sheet == null) {
                    if (barFocused) onSelectTab(TvDestination.Hub) else currentTab.requestFocus()
                }
                CompositionLocalProvider(LocalTvHostedAsOverlay provides true, LocalTvTabHosted provides true) {
                    HandBackHost(
                        entry = issuesEntry,
                        active = sheet == null,
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .then(if (overlay == null && sheet == null) pageTrap else Modifier),
                    ) { content(TvDestination.Issues) { onSelectTab(TvDestination.Hub) } }
                }
            }
            overlay?.let {
                CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
                    Box(modifier = Modifier.fillMaxSize().focusRequester(overlayEntry).then(pageTrap), content = it)
                }
            }
        }
        TvHomeTopRow(
            destinations = TvDestination.entries.filter { it != TvDestination.Issues || showIssues },
            current = tab,
            opener = opener,
            down = contentEntry,
            visible = tabsResolved && showBar,
            focused = barFocused,
            onFocusedChange = { barFocused = it },
            accountName = accountName,
            accountAvatarUrl = accountAvatarUrl,
            onSelect = { if (it.isSheet) onOpenSheet(it) else onSelectTab(it) },
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        )
        // A sheet slides in over everything, the bar included.
        if (sheet != null) {
            BackHandler(enabled = overlay == null, onBack = sheetCloser::close)
            CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
                HandBackHost(
                    entry = remember { FocusRequester() },
                    active = true,
                    modifier = Modifier.tvExitFocusGroup(),
                ) { content(sheet, sheetCloser::close) }
            }
        }
    }
}

/**
 * A surface whose focus is handed back to it, as the rail's content pane does, when a page or grid above it closes.
 *
 * Closing disposes the focused node, and Compose parks focus on the first focusable thing left, which is the wrong
 * card, and a request into a group that already holds focus skips its entry routing. So the surface carries a [TvFocusSink]
 * while nothing in it has focus; the sink takes the parked focus, and the hand-off then enters the content's own group,
 * where the hub's memory lands the card or tile that opened the page (#1509).
 */
@Composable
private fun HandBackHost(
    entry: FocusRequester,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hostHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().onFocusChanged { hostHasFocus = it.hasFocus }.focusGroup()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .focusRequester(entry)
                    .focusGroup(),
        ) { content() }
        if (active && !contentHasFocus) TvFocusSink()
    }
    val sinkHoldsFocus = active && hostHasFocus && !contentHasFocus
    LaunchedEffect(sinkHoldsFocus) {
        if (sinkHoldsFocus) restoreTvOverlayFocus(entry)
    }
}

/**
 * PROTOTYPE: the bar, after Netflix's TV bar. Account at the far left as an icon with a caret, the tabs centred as
 * always-labelled text pills, and Settings at the far right as a gear. The current tab keeps a dim pill when focus is
 * elsewhere; a focused item fills light with dark text.
 */
@Composable
private fun TvHomeTopRow(
    destinations: List<TvDestination>,
    current: TvDestination,
    opener: Map<TvDestination, FocusRequester>,
    down: FocusRequester,
    visible: Boolean,
    focused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    accountName: String,
    accountAvatarUrl: String?,
    onSelect: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (visible) 1f else 0f)
                .onFocusChanged { onFocusedChange(it.hasFocus) }
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0.9f), background.copy(alpha = 0f))))
                .padding(
                    top = dimensionResource(TvR.dimen.tv_overscan_vertical),
                    bottom = dimensionResource(DesR.dimen.padding_m),
                    start = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                    end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                ),
    ) {
        // Smaller while focus is on the page, full size once the bar has it, over a quick scale from its top edge.
        val scale by animateFloatAsState(
            targetValue = if (focused) 1f else TOP_BAR_RESTING_SCALE,
            animationSpec = if (LocalReduceMotion.current) snap() else tween(TOP_BAR_SCALE_MILLIS),
            label = "topBarScale",
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            val item = { destination: TvDestination ->
                Modifier
                    .focusRequester(opener.getValue(destination))
                    .focusProperties {
                        canFocus = visible
                        this.down = down
                    }
            }
            destinations.firstOrNull { it == TvDestination.Account }?.let {
                TvTopNavAvatar(
                    label = stringResource(it.labelRes),
                    name = accountName,
                    avatarUrl = accountAvatarUrl,
                    onClick = { onSelect(it) },
                    modifier = item(it).align(Alignment.CenterStart),
                )
            }
            // Only the tabs scale, so Account and Settings stay pinned to the edges.
            Row(
                modifier =
                    Modifier.align(Alignment.Center).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.filter { !it.isSheet }.forEach { destination ->
                    TvTopNavItem(
                        label = stringResource(destination.labelRes),
                        icon = null,
                        showLabel = true,
                        caret = false,
                        selected = destination == current,
                        onClick = { onSelect(destination) },
                        modifier = item(destination),
                    )
                }
            }
            destinations.firstOrNull { it == TvDestination.Settings }?.let {
                TvTopNavItem(
                    label = stringResource(it.labelRes),
                    icon = it.icon,
                    showLabel = false,
                    caret = false,
                    selected = false,
                    onClick = { onSelect(it) },
                    modifier = item(it).align(Alignment.CenterEnd),
                )
            }
        }
    }
}

private const val TOP_BAR_RESTING_SCALE = 0.8f
private const val TOP_BAR_SCALE_MILLIS = 150

/** The signed-in user's avatar, ringed when focused. */
@Composable
private fun TvTopNavAvatar(
    label: String,
    name: String,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val ring = dimensionResource(TvR.dimen.tv_button_border_width) * 2
    Box(
        modifier =
            modifier
                .size(dimensionResource(TvR.dimen.tv_icon_button_diameter))
                .clip(BingeShapes.Pill)
                .border(ring, if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent, BingeShapes.Pill)
                .tvClickable(role = Role.Button, onFocusChanged = { focused = it }, onClick = onClick)
                .clearAndSetSemantics { contentDescription = label }
                .padding(ring),
        contentAlignment = Alignment.Center,
    ) {
        BingeTvInitialsAvatar(
            name = name,
            size = dimensionResource(TvR.dimen.tv_icon_button_diameter) - ring * 2,
            avatarUrl = avatarUrl,
        )
    }
}

@Composable
private fun TvTopNavItem(
    label: String,
    icon: ImageVector?,
    showLabel: Boolean,
    caret: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val container =
        when {
            focused -> scheme.onSurface
            selected -> scheme.onSurface.copy(alpha = 0.2f)
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
                .clearAndSetSemantics { contentDescription = label }
                .padding(horizontal = dimensionResource(TvR.dimen.tv_icon_button_padding_horizontal) + dimensionResource(DesR.dimen.padding_s)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(dimensionResource(TvR.dimen.tv_button_icon)))
        }
        if (showLabel) Text(text = label, style = MaterialTheme.typography.titleMedium, color = content)
        if (caret) Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null, tint = content)
    }
}
