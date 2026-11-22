package com.cooper.binge.companion.seerr.ui

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

/** Opens a keyboard [height] tall over the window, answering every insets dispatch at the parent. */
@Composable
internal fun WithKeyboard(
    height: Dp,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val px = with(LocalDensity.current) { height.roundToPx() }
    // Read first, so Compose's own insets listener is installed on the view before the dispatch below.
    WindowInsets.systemBars
    DisposableEffect(view, px) {
        val host = view.parent as View
        val platform =
            checkNotNull(
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
                    .toWindowInsets(),
            )
        host.setOnApplyWindowInsetsListener { _, _ -> platform }
        host.dispatchApplyWindowInsets(platform)
        onDispose { host.setOnApplyWindowInsetsListener(null) }
    }
    content()
}
