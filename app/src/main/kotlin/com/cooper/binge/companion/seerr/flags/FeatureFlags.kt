package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.homelab.core.flags.FlagKey
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags

/** The flags for the composition below. Off until a root provides them, so a screen shown alone advertises nothing. */
val LocalFlags = staticCompositionLocalOf { Flags() }

/** Whether the discovery surfaces show: the shell with its tabs, and the page a title opens. Off until a root provides it. */
val LocalDiscoveryEnabled = staticCompositionLocalOf { false }

/**
 * Whether the app shows the discovery surfaces (#2039). Off until Remote Config turns it on, and off in a build with no
 * Firebase app. With it off the app is exactly as it was before the epic.
 */
val DiscoveryEnabled = FlagKey("discovery_enabled")

/**
 * Whether a settings search result for an on/off setting carries its own switch. It is a debug-only switch, so it has no remote
 * value and is deliberately not in [RemoteFlagKeys]: a Remote Config template must never be able to turn it on. Only a debug
 * build's developer override does, and a shipped build never shows the switches.
 */
val SearchToggles = FlagKey("search_toggles_enabled")

/** The flags Remote Config is asked for: the shared ones, and this app's discovery flag. */
val RemoteFlagKeys: List<FlagKey> = SharedFlags.all + DiscoveryEnabled

/** Every flag the developer flags screen lists and can override. */
val AllFlagKeys: List<FlagKey> = RemoteFlagKeys + SearchToggles

/** Anything that asks the user to install, open or connect Binge. */
val Flags.bingePromotion: Boolean get() = this[SharedFlags.BingePromotion]

/** Whether the app may ask the platform to show its review card. */
val Flags.reviewPrompt: Boolean get() = this[SharedFlags.ReviewPrompt]
