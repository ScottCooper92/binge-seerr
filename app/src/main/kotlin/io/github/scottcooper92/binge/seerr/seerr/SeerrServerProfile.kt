package io.github.scottcooper92.binge.seerr.seerr

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

/** Seerr's `MediaServerType`; Overseerr predates the field and is always Plex. */
enum class SeerrMediaServer {
    Plex,
    Jellyfin,
    Emby,
    NotConfigured,
    ;

    companion object {
        private const val PLEX = 1
        private const val JELLYFIN = 2
        private const val EMBY = 3

        fun fromCode(code: Int?): SeerrMediaServer =
            when (code) {
                PLEX, null -> Plex
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
) {
    private val jellyseerrLineage: Boolean get() = variant == SeerrVariant.Jellyseerr || variant == SeerrVariant.Seerr

    /** The blocklist arrived with Jellyseerr 2.0; Overseerr never had one. */
    val hasBlocklist: Boolean get() = jellyseerrLineage && atLeast(2, 0)

    /** Jellyseerr 2.x served it at `/blacklist`; Seerr 3.0 renamed it and keeps the old path as an alias. */
    val blocklistPath: String get() = if (atLeast(3, 0)) "blocklist" else "blacklist"

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

    /** Pushover's sound list: Overseerr 1.34, and the Jellyseerr lineage from 1.8. */
    val hasPushoverSounds: Boolean get() = if (jellyseerrLineage) atLeast(1, 8) else atLeast(1, 34)

    /** Deleting a title's files from Radarr or Sonarr arrived with Jellyseerr 1.5; Overseerr never had it. */
    val hasDeleteMediaFiles: Boolean get() = jellyseerrLineage && atLeast(1, 5)

    /** Tautulli watch data: Overseerr 1.29, and the Jellyseerr lineage from 1.1. */
    val hasWatchData: Boolean get() = if (jellyseerrLineage) atLeast(1, 1) else atLeast(1, 29)

    val mediaServer: SeerrMediaServer get() = SeerrMediaServer.fromCode(settings.mediaServerType)

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
                        SeerrMediaServer.NotConfigured -> Unit
                    }
                }
            }

    private fun atLeast(
        major: Int,
        minor: Int,
    ): Boolean = version?.let { it >= SeerrVersion(major, minor, 0) } ?: true

    companion object {
        /**
         * The lineage from the version's major, as [SeerrVariant.fromVersion]; a development build has
         * none, so its public settings decide — `mediaServerType` exists only on the Jellyseerr lineage.
         */
        fun from(
            status: SeerrStatusDto,
            settings: SeerrPublicSettings,
        ): SeerrServerProfile {
            val version = SeerrVersion.parse(status.version)
            val variant =
                when {
                    version != null -> SeerrVariant.fromVersion(status.version)
                    settings.mediaServerType != null -> SeerrVariant.Seerr
                    else -> SeerrVariant.Overseerr
                }
            return SeerrServerProfile(
                variant = variant,
                version = version,
                commitTag = status.commitTag,
                updateAvailable = status.updateAvailable,
                commitsBehind = status.commitsBehind,
                settings = settings,
            )
        }

        /** Neither call answered: the lineage the credentials recorded, taken at its latest. */
        fun unknown(variant: SeerrVariant): SeerrServerProfile = SeerrServerProfile(variant = variant, version = null)
    }
}

/**
 * Reads the profile off the live server. `/status` and `/settings/public` are both unauthenticated
 * and each best-effort: a failed `/status` leaves the lineage to the settings or to [fallback], and
 * failed settings leave their defaults. The failure is the `/status` one, and only when neither
 * call answered: that is the setup form's "not a Seerr server, or not reachable".
 */
suspend fun SeerrApi.inspectProfile(fallback: SeerrVariant): Result<SeerrServerProfile> {
    val status = runCatching { status() }
    val settings = runCatching { publicSettings() }.getOrNull()
    val statusDto = status.getOrNull()
    return when {
        statusDto != null -> Result.success(SeerrServerProfile.from(statusDto, settings ?: SeerrPublicSettings()))
        settings?.mediaServerType != null -> Result.success(SeerrServerProfile.from(SeerrStatusDto(), settings))
        settings != null -> Result.success(SeerrServerProfile.unknown(fallback).copy(settings = settings))
        else -> Result.failure(status.exceptionOrNull() ?: IllegalStateException("No status"))
    }
}

/** [inspectProfile] for a server that already connected: a profile is always produced, at the lineage's latest. */
suspend fun SeerrApi.readProfile(fallback: SeerrVariant): SeerrServerProfile =
    inspectProfile(fallback).getOrElse { SeerrServerProfile.unknown(fallback) }
