package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.homelab.core.flags.FlagKey
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.core.flags.bingePromotion as sharedBingePromotion
import com.cooper.homelab.core.uiandroid.flags.LocalFlags as SharedLocalFlags

/** Whether the discovery surfaces show: the shell with its tabs, and the page a title opens. Off until a root provides it. */
val LocalDiscoveryEnabled = staticCompositionLocalOf { false }

/**
 * Whether the app shows the discovery surfaces (#2039). On until Remote Config turns it off, so a first run with nothing
 * fetched, and a build with no Firebase app, show discovery. The user's own Discovery setting can still opt out. With it off
 * the app is exactly as it was before the epic.
 */
val DiscoveryEnabled = FlagKey("discovery_enabled", default = true)

/**
 * Whether setup runs on the shared connect machine from the shared library instead of the app's own view model. Off until Remote
 * Config turns it on, so a released build keeps the path it has always had; a debug build's developer override switches it for a check.
 */
val SharedSetupEnabled = FlagKey("shared_setup_enabled")

/**
 * Whether a settings search result for an on/off setting carries its own switch. It is a debug-only switch, so it has no remote
 * value and is deliberately not in [RemoteFlagKeys]: a Remote Config template must never be able to turn it on. Only a debug
 * build's developer override does, and a shipped build never shows the switches.
 */
val SearchToggles = FlagKey("search_toggles_enabled")

/** The flags Remote Config is asked for: the shared ones, and this app's discovery and shared-setup flags. */
val RemoteFlagKeys: List<FlagKey> = SharedFlags.all + DiscoveryEnabled + SharedSetupEnabled

/** Every flag the developer flags screen lists and can override. */
val AllFlagKeys: List<FlagKey> = RemoteFlagKeys + SearchToggles

/**
 * Temporary: `RequestOpenSheet` is frozen while the `:data` split lands (#2163), so it still imports these two. They are the shared
 * library's own, not copies, so a root that provides one provides the other. Remove them, and that file's imports, once it is free.
 */
val LocalFlags: ProvidableCompositionLocal<Flags> = SharedLocalFlags

val Flags.bingePromotion: Boolean get() = sharedBingePromotion
