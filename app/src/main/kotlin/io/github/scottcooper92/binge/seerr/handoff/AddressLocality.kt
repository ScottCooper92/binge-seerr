package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.normaliseBaseUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Whether a server address is one a television on the same home network can be expected to reach,
 * judged on the host as written and never on what it resolves to.
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

private const val IPV4_OCTETS = 4
private const val MAX_OCTET = 255

/** A network as an address and a prefix length, written the usual way (`10.0.0.0/8`) so no number needs explaining. */
private class Cidr(
    spec: String,
) {
    private val network: ByteArray = InetAddress.getByName(spec.substringBefore('/')).address
    private val bits: Int = spec.substringAfter('/').toInt()

    operator fun contains(address: ByteArray): Boolean =
        address.size == network.size && (0 until bits).all { address.bitAt(it) == network.bitAt(it) }

    private fun ByteArray.bitAt(index: Int): Int =
        (this[index / Byte.SIZE_BITS].toInt() shr (Byte.SIZE_BITS - 1 - index % Byte.SIZE_BITS)) and 1
}

/** Private (RFC 1918), link-local and loopback IPv4. */
private val LOCAL_IPV4 = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "127.0.0.0/8").map(::Cidr)

/** Loopback, link-local and unique-local IPv6. */
private val LOCAL_IPV6 = listOf("::1/128", "fe80::/10", "fc00::/7").map(::Cidr)

/** Tailscale's block: unique-local in form, an overlay network in practice, so not local. */
private val OVERLAY_IPV6 = Cidr("fd7a:115c:a1e0::/48")

/** What an IPv6 literal, lowercased, is written with; anything else is a name and never reaches the parser. */
private val IPV6_CHARS = "0123456789abcdef:.".toSet()

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
    val ip = ipv4Bytes(name) ?: ipv6Bytes(name)
    return when {
        ip != null -> if (ip.isLocalIp()) AddressLocality.Local else AddressLocality.NotLocal
        name.isEmpty() -> AddressLocality.Unknown
        name.endsWith(".local") || !name.contains('.') -> AddressLocality.Local
        else -> AddressLocality.Unknown
    }
}

private fun ByteArray.isLocalIp(): Boolean =
    if (size == IPV4_OCTETS) LOCAL_IPV4.any { this in it } else LOCAL_IPV6.any { this in it } && this !in OVERLAY_IPV6

/** A dotted-quad IPv4 literal, read here rather than by [InetAddress], which would look up anything else. */
private fun ipv4Bytes(name: String): ByteArray? =
    name
        .split('.')
        .map { it.toIntOrNull() }
        .takeIf { octets -> octets.size == IPV4_OCTETS && octets.all { it != null && it in 0..MAX_OCTET } }
        ?.map { (it ?: 0).toByte() }
        ?.toByteArray()

/**
 * The bytes of an IPv6 literal, or null for anything else. A literal is parsed by
 * [InetAddress.getByName] without a lookup, and only something written like one reaches it. An
 * IPv4-mapped literal (`::ffff:192.168.1.2`) comes back as the four bytes of the IPv4 address it maps.
 */
private fun ipv6Bytes(name: String): ByteArray? {
    if (!name.contains(':') || !name.all { it in IPV6_CHARS }) return null
    return when (val parsed = runCatching { InetAddress.getByName(name) }.getOrNull()) {
        is Inet6Address, is Inet4Address -> parsed.address
        else -> null
    }
}
