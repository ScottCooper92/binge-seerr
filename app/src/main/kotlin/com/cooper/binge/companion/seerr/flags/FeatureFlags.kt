package com.cooper.binge.companion.seerr.flags

import com.cooper.homelab.core.flags.FlagKey
import com.cooper.homelab.core.flags.SharedFlags

/**
 * Whether the app shows the discovery surfaces (#2039). On until Remote Config turns it off, so a first run with nothing
 * fetched, and a build with no Firebase app, show discovery. The user's own Discovery setting can still opt out. With it off
 * the app is exactly as it was before the epic.
 */
val DiscoveryEnabled = FlagKey("discovery_enabled", default = true)

/**
 * Whether the television shows the discovery tabs (Search, Movies and TV) in its top bar, and opens on Movies. Off until Remote Config
 * turns it on, and separate from [DiscoveryEnabled], which is the phone's: a TV user sees Requests and Issues until the TV discovery
 * screens have content. A debug build's developer override switches it for a check.
 */
val TvDiscoveryEnabled = FlagKey("tv_discovery_enabled")

/**
 * Whether a settings search result for an on/off setting carries its own switch. It is a debug-only switch, so it has no remote
 * value and is deliberately not in [RemoteFlagKeys]: a Remote Config template must never be able to turn it on. Only a debug
 * build's developer override does, and a shipped build never shows the switches.
 */
val SearchToggles = FlagKey("search_toggles_enabled")

/** The flags Remote Config is asked for: the shared ones, and this app's discovery and TV discovery flags. */
val RemoteFlagKeys: List<FlagKey> = SharedFlags.all + DiscoveryEnabled + TvDiscoveryEnabled

/** Every flag the developer flags screen lists and can override. */
val AllFlagKeys: List<FlagKey> = RemoteFlagKeys + SearchToggles
