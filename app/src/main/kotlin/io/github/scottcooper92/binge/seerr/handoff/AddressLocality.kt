package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.AddressRange
import io.github.scottcooper92.binge.seerr.seerr.ipLiteralBytes
import io.github.scottcooper92.binge.seerr.seerr.isIn
import io.github.scottcooper92.binge.seerr.seerr.normaliseBaseUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Whether a server address is one a television on the same home network can be expected to reach,
 * judged on the host as written and never on what it resolves to.
 *
 * This is not `isLocalOrPrivateHost`, which answers the cleartext-consent question and counts the
 * CGNAT and Tailscale ranges as local. The two disagree on purpose; do not unify them.
 */
enum class AddressLocality {
    /** A private, link-local or loopback IP literal, a `.local` name, or a single-label name such as `nas`. */
    Local,

    /**
     * Any other IP literal: a public address, and the overlay ranges a VPN hands out — the CGNAT range
     * `100.64.0.0/10` and Tailscale's `fd7a:115c:a1e0::/48` — which a TV not on that overlay cannot reach.
     */
    NotLocal,

    /** Any other name. Only DNS could tell, and this does not ask it. */
    Unknown,
}

/** The locality of the host in [address], or null when [address] is not a server address at all. */
fun addressLocality(address: String): AddressLocality? =
    address
        .normaliseBaseUrl()
        .toHttpUrlOrNull()
        ?.host
        ?.let(::hostLocality)

/**
 * The locality of [host], as `HttpUrl` gives it: lowercase, and an IPv6 literal without its brackets.
 * Pure: an IP literal is read from its digits and a name from its shape, so nothing is resolved.
 */
fun hostLocality(host: String): AddressLocality {
    val name =
        host
            .trim()
            .trimEnd('.')
            .removePrefix("[")
            .removeSuffix("]")
            .lowercase()
    val ip = ipLiteralBytes(name)
    return when {
        ip != null -> if (ip.isLocalIp()) AddressLocality.Local else AddressLocality.NotLocal
        name.isEmpty() -> AddressLocality.Unknown
        name.endsWith(".local") || !name.contains('.') -> AddressLocality.Local
        else -> AddressLocality.Unknown
    }
}

/** Loopback, link-local, RFC 1918 and unique-local, bar Tailscale's block: an overlay network in practice, so not local. */
private fun ByteArray.isLocalIp(): Boolean =
    isIn(AddressRange.Loopback, AddressRange.LinkLocal, AddressRange.Private, AddressRange.UniqueLocal) &&
        !isIn(AddressRange.Tailscale)
