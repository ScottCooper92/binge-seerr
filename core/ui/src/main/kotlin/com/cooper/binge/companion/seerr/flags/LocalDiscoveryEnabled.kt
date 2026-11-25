package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the discovery surfaces show: the shell with its tabs, and the page a title opens. Off until a root provides it. */
val LocalDiscoveryEnabled = staticCompositionLocalOf { false }
