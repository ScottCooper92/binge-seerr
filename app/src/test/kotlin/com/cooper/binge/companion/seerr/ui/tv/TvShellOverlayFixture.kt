package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.testing.TakeDownAndroidComposeRule

/** The test tag on [ShellWithOverlay]'s stand-in page under the bar. */
internal const val OVERLAY_TAG = "shell-overlay"

/** The test tag on [ShellWithOverlay]'s stand-in page over the bar. */
internal const val TOP_LAYER_TAG = "shell-top-layer"

/** Frames enough for the shell's hand-off out of its focus sink, which is frame-paced, to land. */
private const val HAND_OFF_FRAMES = 30

/**
 * A board in the real shell, with stand-ins for what it opens: a see-all page under the bar while [overlayOpen], and a title's
 * own page over the bar while [topLayerOpen], as the request and issue pages are. Each takes focus and closes on Back. A
 * test of focus coming back from one of those has to run through the shell, because the shell is what hands it back.
 */
@Composable
internal fun ShellWithOverlay(
    destination: TvDestination,
    overlayOpen: Boolean,
    onCloseOverlay: () -> Unit,
    topLayerOpen: Boolean = false,
    onCloseTopLayer: () -> Unit = {},
    board: @Composable () -> Unit,
) {
    BoardInShell(
        destination,
        overlay = if (overlayOpen) ({ StandInPage(onCloseOverlay) }) else null,
        topLayer = if (topLayerOpen) ({ StandInPage(onCloseTopLayer, TOP_LAYER_TAG) }) else null,
        board = board,
    )
}

/** [board] as [destination] shows it in the real shell: Home itself, or the page above Home its button opens. */
@Composable
internal fun BoardInShell(
    destination: TvDestination,
    overlay: (@Composable BoxScope.() -> Unit)?,
    topLayer: (@Composable () -> Unit)? = null,
    board: @Composable () -> Unit,
) {
    TvHomeShell(
        tab = if (destination.isSheet) TvDestination.Hub else destination,
        sheet = destination.takeIf { it.isSheet },
        onSelectTab = {},
        onOpenSheet = {},
        onCloseSheet = {},
        overlay = overlay,
        topLayer = topLayer,
    ) { shown, _ ->
        if (shown == destination) board()
    }
}

@Composable
internal fun StandInPage(
    onClose: () -> Unit,
    tag: String = OVERLAY_TAG,
) {
    val requester = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .testTag(tag)
            .focusRequester(requester)
            .focusable(),
    )
    LaunchedEffect(Unit) { requester.requestFocus() }
}

internal fun TakeDownAndroidComposeRule<*>.pressBack() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

internal fun TakeDownAndroidComposeRule<*>.press(key: Key) {
    onRoot().performKeyInput { pressKey(key) }
    waitForIdle()
}

/** Lets the shell hand focus back from its sink once the overlay above Home has closed. */
internal fun TakeDownAndroidComposeRule<*>.settleShellHandOff() {
    repeat(HAND_OFF_FRAMES) { mainClock.advanceTimeByFrame() }
    waitForIdle()
}
