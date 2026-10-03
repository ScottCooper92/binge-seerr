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

/** Where each fork publishes its releases: the "update available" row's destination. */
fun SeerrVariant.releaseNotesUrl(): String =
    when (this) {
        SeerrVariant.Overseerr -> "https://github.com/sct/overseerr/releases"
        SeerrVariant.Jellyseerr -> "https://github.com/fallenbagel/jellyseerr/releases"
        SeerrVariant.Seerr, SeerrVariant.Unknown -> "https://github.com/seerr-team/seerr/releases"
    }

/** Each fork's documentation. */
fun SeerrVariant.docsUrl(): String =
    when (this) {
        SeerrVariant.Overseerr -> "https://docs.overseerr.dev"
        SeerrVariant.Jellyseerr -> "https://docs.jellyseerr.dev"
        SeerrVariant.Seerr, SeerrVariant.Unknown -> "https://docs.seerr.dev"
    }

/** Each fork's Discord invite; Seerr kept Jellyseerr's server. */
fun SeerrVariant.discordUrl(): String =
    when (this) {
        SeerrVariant.Overseerr -> "https://discord.gg/overseerr"
        SeerrVariant.Jellyseerr, SeerrVariant.Seerr, SeerrVariant.Unknown -> "https://discord.gg/ckbvBtDJgC"
    }

/** Each fork's repository. */
fun SeerrVariant.githubUrl(): String = releaseNotesUrl().removeSuffix("/releases")

/** Only an `http(s)` address is handed to a browser. */
fun String.isWebUrl(): Boolean = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/** Whether [this] parses as a base URL once normalised — the check a setup screen makes before connecting. */
fun String.isValidBaseUrl(): Boolean = normaliseBaseUrl().toHttpUrlOrNull() != null

private const val DEFAULT_SEERR_PORT = 5055

/**
 * Whether the already scheme-normalised [this] (see [normaliseBaseUrl]) names an explicit port.
 * The normaliser guarantees a scheme and a trailing slash, so the authority is exactly what sits
 * between `://` and the next `/` — bracketed for IPv6 — and a port is a `:` right after it.
 */
internal fun String.hasExplicitPort(): Boolean {
    val authority = substringAfter("://", "").substringBefore("/")
    return if (authority.startsWith("[")) authority.substringAfter("]").startsWith(":") else authority.contains(":")
}

/**
 * [this], already normalised and portless, with [DEFAULT_SEERR_PORT] — the port every fork's
 * Docker image serves on — added. The second candidate `SeerrConnection.inspect` tries when the
 * entered address carried no port of its own.
 */
internal fun String.withDefaultSeerrPort(): String? =
    toHttpUrlOrNull()
        ?.newBuilder()
        ?.port(DEFAULT_SEERR_PORT)
        ?.build()
        ?.toString()

private fun String.isLocalHostAddress(): Boolean {
    // Prefixing a scheme lets HttpUrl extract the host — it handles ports, paths and bracketed IPv6.
    val host = "http://$this".toHttpUrlOrNull()?.host ?: return false
    return host.isLocalOrPrivateHost()
}
