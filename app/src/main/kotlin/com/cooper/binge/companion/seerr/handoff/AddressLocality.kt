package com.cooper.binge.companion.seerr.handoff

import com.cooper.binge.companion.seerr.seerr.normaliseBaseUrl
import com.cooper.homelab.core.server.network.AddressLocality
import com.cooper.homelab.core.server.network.hostLocality
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The locality of the host in [address], or null when [address] is not a server address at all. */
fun addressLocality(address: String): AddressLocality? =
    address
        .normaliseBaseUrl()
        .toHttpUrlOrNull()
        ?.host
        ?.let(::hostLocality)
