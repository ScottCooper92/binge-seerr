package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags

/** The flags for the composition below. Off until a root provides them, so a screen shown alone advertises nothing. */
val LocalFlags = staticCompositionLocalOf { Flags() }

/** Whether the discovery surfaces show. Off until a root provides it; nothing reads it yet. */
val LocalDiscoveryEnabled = staticCompositionLocalOf { false }

/** Anything that asks the user to install, open or connect Binge. */
val Flags.bingePromotion: Boolean get() = this[SharedFlags.BingePromotion]

/** Whether the app may ask the platform to show its review card. */
val Flags.reviewPrompt: Boolean get() = this[SharedFlags.ReviewPrompt]
