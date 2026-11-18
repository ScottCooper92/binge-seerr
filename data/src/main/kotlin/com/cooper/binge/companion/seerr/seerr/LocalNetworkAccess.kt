package com.cooper.binge.companion.seerr.seerr

import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.server.network.isLocalNetworkHost

/**
 * The host of this server address when reaching it needs the local-network permission, judged on the
 * name as typed by [isLocalNetworkHost]. Null for a public, loopback or tailnet host, and for an
 * address that does not parse.
 */
fun String.localNetworkHostOrNull(): String? = ServerAddress.parse(this)?.host?.takeIf { isLocalNetworkHost(it) }

/** Whether [this] address needs the permission and [permission] does not hold it. */
fun String.isBlockedByLocalNetwork(permission: LocalNetworkPermission): Boolean =
    localNetworkHostOrNull() != null && !permission.isGranted()
