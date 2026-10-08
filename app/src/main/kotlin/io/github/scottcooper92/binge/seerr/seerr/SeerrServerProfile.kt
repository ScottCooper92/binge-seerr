package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A release version, `major.minor.patch`; a `develop-<sha>` or `local` build has none. */
data class SeerrVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SeerrVersion> {
    override fun compareTo(other: SeerrVersion): Int =
        compareValuesBy(this, other, SeerrVersion::major, SeerrVersion::minor, SeerrVersion::patch)

    val label: String get() = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String?): SeerrVersion? {
            val parts = raw?.trim()?.removePrefix("v")?.split('.') ?: return null
            if (parts.size < 2) return null
            val numbers = parts.take(3).map { it.takeWhile(Char::isDigit).toIntOrNull() ?: return null }
            return SeerrVersion(numbers[0], numbers[1], numbers.getOrElse(2) { 0 })
        }
    }
}

/**
 * Seerr's `MediaServerType`; Overseerr predates the field and is always Plex. [Unknown] is a server
 * whose type was never read, which is not the same as [NotConfigured] and must not be shown as Plex.
 */
enum class SeerrMediaServer {
    Plex,
    Jellyfin,
    Emby,
    NotConfigured,
    Unknown,
    ;

    companion object {
        private const val PLEX = 1
        private const val JELLYFIN = 2
        private const val EMBY = 3

        fun fromCode(code: Int?): SeerrMediaServer =
            when (code) {
                PLEX -> Plex
                null -> Unknown
                JELLYFIN -> Jellyfin
                EMBY -> Emby
                else -> NotConfigured
            }
    }
}

/** A way of signing in the connected server accepts. The API key is every server's. */
enum class SeerrSignInMode { ApiKey, Local, Plex, Jellyfin, Emby, QuickConnect }

/**
 * What the connected server is and what it can do, read once per connection from `/status` and
 * `/settings/public`. The one place a version is compared: every screen and the Service ask for a
 * named capability, and never for the number. `docs/server-compatibility.md` is the policy.
 */
data class SeerrServerProfile(
    val variant: SeerrVariant,
    /** Null for a development build, which is taken to have everything its lineage has released. */
    val version: SeerrVersion?,
    val commitTag: String? = null,
    val updateAvailable: Boolean = false,
    val commitsBehind: Int = 0,
    val settings: SeerrPublicSettings = SeerrPublicSettings(),
    /** False when a call failed, so the lineage or the settings are guesses; an incomplete profile is not cached. */
    val complete: Boolean = true,
) {
    /** An unknown lineage hides nothing, as [unknown] promises: it is taken as the family's latest, never as Overseerr. */
    private val jellyseerrLineage: Boolean get() = variant.isJellyseerrLineage || variant == SeerrVariant.Unknown

    /** The blocklist arrived with Jellyseerr 2.0; Overseerr never had one. */
    val hasBlocklist: Boolean get() = jellyseerrLineage && atLeast(2, 0)

    /** Jellyseerr 2.x served it at `/blacklist`; Seerr 3.0 renamed it and keeps the old path as an alias. */
    val blocklistPath: String get() = if (atLeast(3, 0)) "blocklist" else "blacklist"

    /** Seerr 3.2 requires the media type on an unblock; an earlier server's validator rejects it as an unknown query parameter. */
    fun unblockMediaType(mediaType: String): String? = mediaType.takeIf { atLeast(major = 3, minor = 2) }

    val canBlockCollections: Boolean get() = jellyseerrLineage && atLeast(3, 2)

    /** Tag-driven blocking, and with it the list's `filter`, came with the rename at Seerr 3.0. */
    val hasBlocklistFilters: Boolean get() = jellyseerrLineage && atLeast(3, 0)

    /** Overseerr grew issues at 1.28 and their counts at 1.30; the Jellyseerr lineage always had both. */
    val hasIssues: Boolean get() = jellyseerrLineage || atLeast(1, 28)

    val hasCounts: Boolean get() = jellyseerrLineage || atLeast(1, 30)

    val hasOverrideRules: Boolean get() = jellyseerrLineage && atLeast(2, 2)

    val hasNetworkSettings: Boolean get() = jellyseerrLineage && atLeast(2, 4)

    /** Choosing TMDB or TVDB for series and anime arrived with Seerr 3.0. */
    val hasMetadataSettings: Boolean get() = jellyseerrLineage && atLeast(3, 0)

    val hasLinkedAccounts: Boolean get() = jellyseerrLineage && atLeast(2, 4)

    val hasQuickConnect: Boolean get() = jellyseerrLineage && atLeast(3, 4)

    val hasDiscoverSliders: Boolean get() = jellyseerrLineage || atLeast(1, 32)

    /** The ntfy agent arrived with Jellyseerr 2.6; Overseerr never had it. */
    val hasNtfy: Boolean get() = jellyseerrLineage && atLeast(2, 6)

    /** LunaSea is Overseerr's alone: the Jellyseerr lineage dropped the agent. */
    val hasLunaSea: Boolean get() = variant == SeerrVariant.Overseerr

    /** Gotify: Overseerr 1.29, and the Jellyseerr lineage from 1.1. */
    val hasGotify: Boolean get() = if (jellyseerrLineage) atLeast(1, 1) else atLeast(1, 29)

    /** A Telegram topic, per user and on the server's agent: Jellyseerr 2.2 added one, and Overseerr never had it. */
    val hasTelegramTopics: Boolean get() = jellyseerrLineage && atLeast(2, 2)

    /** Pushover's sound list: Overseerr 1.34, and the Jellyseerr lineage from 1.8. */
    val hasPushoverSounds: Boolean get() = if (jellyseerrLineage) atLeast(1, 8) else atLeast(1, 34)

    /** Deleting a title's files from Radarr or Sonarr arrived with Jellyseerr 1.5; Overseerr never had it. */
    val hasDeleteMediaFiles: Boolean get() = jellyseerrLineage && atLeast(1, 5)

    /** Tautulli watch data: Overseerr 1.29, and the Jellyseerr lineage from 1.1. */
    val hasWatchData: Boolean get() = if (jellyseerrLineage) atLeast(1, 1) else atLeast(1, 29)

    /** Auto-Request, View Recently Added and View Watchlists: Jellyseerr 1.2 and Overseerr 1.30 added the three together. */
    val hasWatchlistPermissions: Boolean get() = if (jellyseerrLineage) atLeast(1, 2) else atLeast(1, 30)

    /** Overseerr has no such field and is always Plex; on any other lineage a missing type is unknown. */
    val mediaServer: SeerrMediaServer
        get() =
            if (variant == SeerrVariant.Overseerr && settings.mediaServerType == null) {
                SeerrMediaServer.Plex
            } else {
                SeerrMediaServer.fromCode(settings.mediaServerType)
            }

    /** The sign-in modes the form offers: what the lineage can do, narrowed by what the admin turned on. */
    val signInModes: Set<SeerrSignInMode>
        get() =
            buildSet {
                add(SeerrSignInMode.ApiKey)
                if (settings.localLogin) add(SeerrSignInMode.Local)
                val mediaServerLogin = if (jellyseerrLineage) settings.mediaServerLogin else true
                if (mediaServerLogin) {
                    when (mediaServer) {
                        SeerrMediaServer.Plex -> add(SeerrSignInMode.Plex)
                        SeerrMediaServer.Jellyfin -> {
                            add(SeerrSignInMode.Jellyfin)
                            if (hasQuickConnect) add(SeerrSignInMode.QuickConnect)
                        }
                        SeerrMediaServer.Emby -> add(SeerrSignInMode.Emby)
                        SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> Unit
                    }
                }
            }

    private fun atLeast(
        major: Int,
        minor: Int,
    ): Boolean = version?.let { it >= SeerrVersion(major, minor, 0) } ?: true

    companion object {
        /**
         * The lineage from the version's major, as [SeerrVariant.fromVersion], except that a `1.x` is
         * both Overseerr and early Jellyseerr, so it and a development build (which has no version at
         * all) are decided by the public settings: `mediaServerType` exists only on the Jellyseerr
         * lineage. Without settings a `1.x` stays Overseerr, a development build takes [fallback],
         * and either way the profile is incomplete and read again.
         */
        fun from(
            status: SeerrStatusDto,
            settings: SeerrPublicSettings?,
            fallback: SeerrVariant = SeerrVariant.Unknown,
        ): SeerrServerProfile {
            val version = SeerrVersion.parse(status.version)
            val byVersion = SeerrVariant.fromVersion(status.version)
            val hasMediaServerType = settings?.mediaServerType != null
            val variant =
                when {
                    version == null && settings == null -> fallback
                    version == null -> if (hasMediaServerType) SeerrVariant.Seerr else SeerrVariant.Overseerr
                    byVersion == SeerrVariant.Overseerr && hasMediaServerType -> SeerrVariant.Jellyseerr
                    else -> byVersion
                }
            return SeerrServerProfile(
                variant = variant,
                version = version,
                commitTag = status.commitTag,
                updateAvailable = status.updateAvailable,
                commitsBehind = status.commitsBehind,
                settings = settings ?: SeerrPublicSettings(),
                complete = settings != null,
            )
        }

        /** Neither call answered: the lineage the credentials recorded, taken at its latest. */
        fun unknown(variant: SeerrVariant): SeerrServerProfile = SeerrServerProfile(variant = variant, version = null, complete = false)
    }
}

/**
 * Reads the profile off the live server. `/status` and `/settings/public` are both unauthenticated
 * and each best-effort: a failed `/status` leaves the lineage to the settings or to [fallback], and
 * failed settings leave their defaults. The failure is the `/status` one, and only when neither
 * call answered: that is the setup form's "not a Seerr server, or not reachable".
 *
 * The two calls do not depend on each other, so they run concurrently: an unreachable server costs
 * one timeout rather than two. Cancellation still propagates, from either call.
 */
suspend fun SeerrApi.inspectProfile(fallback: SeerrVariant): Result<SeerrServerProfile> {
    val (status, settings) =
        coroutineScope {
            val settingsRead = async { attempt { publicSettings() }.getOrNull() }
            attempt { status() } to settingsRead.await()
        }
    val statusDto = status.getOrNull()
    return when {
        statusDto != null -> Result.success(SeerrServerProfile.from(statusDto, settings, fallback))
        settings?.mediaServerType != null -> Result.success(SeerrServerProfile.from(SeerrStatusDto(), settings))
        settings != null -> Result.success(SeerrServerProfile.unknown(fallback).copy(settings = settings))
        else -> Result.failure(status.exceptionOrNull() ?: IllegalStateException("No status"))
    }
}

/** [inspectProfile] for a server that already connected: a profile is always produced, at the lineage's latest. */
suspend fun SeerrApi.readProfile(fallback: SeerrVariant): SeerrServerProfile =
    inspectProfile(fallback).getOrElse { SeerrServerProfile.unknown(fallback) }
