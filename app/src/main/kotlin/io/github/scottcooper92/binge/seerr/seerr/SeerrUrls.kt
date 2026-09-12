package io.github.scottcooper92.binge.seerr.seerr

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Ensures a user-entered address has a scheme and a trailing slash so Retrofit treats it as a
 * base. An explicit scheme is honoured; a schemeless address defaults to `http` for a local or
 * private host — a self-hosted Seerr on a LAN is plain HTTP — and `https` otherwise.
 */
fun String.normaliseBaseUrl(): String {
    val trimmed = trim()
    val withScheme =
        when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.isLocalHostAddress() -> "http://$trimmed"
            else -> "https://$trimmed"
        }
    return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
}

private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/** A TMDB `backdrop_path` (`/abc.jpg`), as `GET backdrops` returns them, to the image URL at sign-in width. */
fun String.toTmdbBackdropUrl(): String = TMDB_BACKDROP_BASE + withLeadingSlash()

/** A TMDB `posterPath`, as a title lookup returns it, to the image URL at row and card width. */
fun String.toTmdbPosterUrl(): String = TMDB_POSTER_BASE + withLeadingSlash()

private fun String.withLeadingSlash(): String = if (startsWith("/")) this else "/$this"

/** Whether [this] parses as a base URL once normalised — the check a setup screen makes before connecting. */
fun String.isValidBaseUrl(): Boolean = normaliseBaseUrl().toHttpUrlOrNull() != null

/**
 * True when [this] explicitly opts into cleartext for a PUBLIC host: an `http://` scheme whose
 * host is not loopback, private, LAN or tailnet. The secret would cross the network unencrypted,
 * so setup warns.
 */
fun String.isInsecurePublicUrl(): Boolean {
    val trimmed = trim()
    if (!trimmed.startsWith("http://", ignoreCase = true)) return false
    val host = trimmed.toHttpUrlOrNull()?.host
    return host != null && !host.isLocalOrPrivateHost()
}

private val PRIVATE_CLASS_B = Regex("""172\.(1[6-9]|2\d|3[01])\..*""")
private val CGNAT_TAILSCALE = Regex("""100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\..*""")

private fun String.isLocalHostAddress(): Boolean {
    // Prefixing a scheme lets HttpUrl extract the host — it handles ports, paths and bracketed IPv6.
    val host = "http://$this".toHttpUrlOrNull()?.host ?: return false
    return host.isLocalOrPrivateHost()
}

/**
 * Loopback, the RFC 1918 ranges, `.local` and single-label names, and Tailscale-style addresses —
 * the CGNAT range, `.ts.net` MagicDNS names, IPv6 ULA — where a self-hosted server typically
 * speaks plain HTTP over an already-encrypted link.
 */
internal fun String.isLocalOrPrivateHost(): Boolean =
    this == "localhost" ||
        this == "127.0.0.1" ||
        this == "::1" ||
        endsWith(".local") ||
        startsWith("10.") ||
        startsWith("192.168.") ||
        PRIVATE_CLASS_B.matches(this) ||
        CGNAT_TAILSCALE.matches(this) ||
        endsWith(".ts.net") ||
        (contains(':') && (startsWith("fd") || startsWith("fc"))) ||
        (!contains('.') && !contains(':'))
