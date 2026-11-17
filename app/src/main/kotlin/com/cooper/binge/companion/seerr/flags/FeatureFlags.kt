package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.homelab.core.flags.Flags

/** The flags for the composition below. Off until a root provides them, so a screen shown alone advertises nothing. */
val LocalFlags = staticCompositionLocalOf { Flags() }

/** Whether the discovery surfaces show. Off until a root provides it; nothing reads it yet. */
val LocalDiscoveryEnabled = staticCompositionLocalOf { false }
