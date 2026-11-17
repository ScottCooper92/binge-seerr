package com.cooper.binge.companion.seerr.handoff

import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.network.AddressLocality
import com.cooper.homelab.core.server.network.hostLocality

/** The locality of the host in [address], or null when [address] is not a server address at all. */
fun addressLocality(address: String): AddressLocality? =
    ServerAddress.parse(address)?.host?.let(::hostLocality)
