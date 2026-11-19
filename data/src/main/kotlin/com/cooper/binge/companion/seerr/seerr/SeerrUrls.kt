package com.cooper.binge.companion.seerr.seerr

import com.cooper.homelab.core.server.address.AddressScheme
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.network.isPublicCleartext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A user-entered address as the base URL Retrofit needs, in the shared library's form ([ServerAddress.parse]): a scheme added
 * where it was left off (`http` for a local or private host, `https` otherwise), the scheme and host lowercased, a default port,
 * user info, query and fragment dropped, dot segments resolved and a trailing slash. An address that is not one comes back as
 * it was typed: check [isValidBaseUrl] first.
 */
fun String.normaliseBaseUrl(): String = ServerAddress.parse(this)?.url ?: trim()

private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"
private const val TMDB_LOGO_BASE = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)"

/** A TMDB `backdrop_path` (`/abc.jpg`), as `GET backdrops` returns them, to the image URL at sign-in width. */
fun String.toTmdbBackdropUrl(): String = TMDB_BACKDROP_BASE + withLeadingSlash()

/** A TMDB `posterPath`, as a title lookup returns it, to the image URL at row and card width. */
fun String.toTmdbPosterUrl(): String = TMDB_POSTER_BASE + withLeadingSlash()

/** A TMDB company `logo_path`, to the image URL at tile width, drawn through TMDB's duotone filter as the web client does. */
fun String.toTmdbLogoUrl(): String = TMDB_LOGO_BASE + withLeadingSlash()

private fun String.withLeadingSlash(): String = if (startsWith("/")) this else "/$this"

/**
 * Where each fork publishes its releases: the "update available" destination, in the chip, the Version row and the TV's code.
 * Jellyseerr's project moved into Seerr's, and its old releases address redirects there, so both open Seerr's.
 */
fun SeerrVariant.releaseNotesUrl(): String =
    when (this) {
        SeerrVariant.Overseerr -> "https://github.com/sct/overseerr/releases"
        SeerrVariant.Jellyseerr, SeerrVariant.Seerr, SeerrVariant.Unknown -> "https://github.com/seerr-team/seerr/releases"
    }

/** Each fork's documentation. */
fun SeerrVariant.docsUrl(): String =
    when (this) {
        SeerrVariant.Overseerr -> "https://docs.overseerr.dev"
        SeerrVariant.Jellyseerr -> "https://docs.jellyseerr.dev"
        SeerrVariant.Seerr, SeerrVariant.Unknown -> "https://docs.seerr.dev"
    }

/** Each fork's Discord invite; Seerr kept Jellyseerr's server, under its own name (`SettingsAbout` in each fork). */
fun SeerrVariant.discordUrl(): String =
    when (this) {
        SeerrVariant.Overseerr -> "https://discord.gg/overseerr"
        SeerrVariant.Jellyseerr, SeerrVariant.Seerr, SeerrVariant.Unknown -> "https://discord.gg/seerr"
    }

/** Each fork's repository. */
fun SeerrVariant.githubUrl(): String = releaseNotesUrl().removeSuffix("/releases")

/** Where each fork takes questions on GitHub: its Discussions, as the web client's About page links. */
fun SeerrVariant.discussionsUrl(): String = githubUrl() + "/discussions"

/**
 * Where each fork takes contributions, as the web client's About page links: Seerr's Open Collective, and GitHub
 * Sponsors for Overseerr and Jellyseerr (`sct`, checked in Jellyseerr 1.9.2 and 2.7.3).
 */
fun SeerrVariant.contributeUrl(): String =
    when (this) {
        SeerrVariant.Overseerr, SeerrVariant.Jellyseerr -> "https://github.com/sponsors/sct"
        SeerrVariant.Seerr, SeerrVariant.Unknown -> "https://opencollective.com/seerr"
    }

/**
 * The host, when [this] explicitly asks for plain HTTP to a PUBLIC one: an `http://` scheme whose host is not loopback,
 * private, LAN or tailnet. The secret would cross the network unencrypted, so setup refuses it unless the user opts in for
 * that host (see `CleartextConsent`). Null for anything else. The host is OkHttp's spelling of it (punycode, compressed IPv6),
 * because that is the one the cleartext guard sees on the request and so the one a consent is keyed on.
 */
fun String.insecurePublicHostOrNull(): String? {
    if (!trim().startsWith("http://", ignoreCase = true)) return null
    val address = ServerAddress.parse(this)?.takeIf { isPublicCleartext(it.scheme == AddressScheme.Https, it.host) } ?: return null
    return address.url.toHttpUrlOrNull()?.host
}

/** Only an `http(s)` address is handed to a browser. */
fun String.isWebUrl(): Boolean = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/**
 * Whether [url] can be a real site: http or https, and not under `.invalid`, the reserved name that never resolves. The
 * demo's address is one, so a title there goes nowhere rather than to a dead page, and nothing here knows of the demo.
 */
fun isOpenableSite(url: String): Boolean = url.toHttpUrlOrNull()?.host?.let { it != "invalid" && !it.endsWith(".invalid") } ?: false

/** Whether [this] parses as a base URL once normalised — the check a setup screen makes before connecting. */
fun String.isValidBaseUrl(): Boolean = ServerAddress.parse(this) != null
