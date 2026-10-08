package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsUpdateBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.isJellyseerrLineage
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.ui.LanguageCodeShapes

/** The highest TCP port there is; a typed port outside 1..this is not one. */
private const val PORT_MAX = 65_535

/**
 * Whether a service is addressable as typed. Four settings forms ask it — the media server, its
 * Tautulli, each DVR instance and the proxy — so they ask it the same way rather than each
 * carrying the range.
 */
internal fun hostAndPortValid(
    host: String,
    port: String,
): Boolean = host.isNotBlank() && portValid(port)

/**
 * Whether a typed port is one: a whole number in 1..65535, once surrounding spaces are trimmed. A
 * form that marks the port field on its own asks this, so it does not need a host to pass.
 */
internal fun portValid(port: String): Boolean = port.trim().toIntOrNull()?.let { it in 1..PORT_MAX } == true

/** The server's own settings pages, for a user who manages settings. */
enum class ServerSettingsPage {
    General,
    Users,
    DefaultPermissions,
    MediaServer,
    Tautulli,
    Services,
    NotificationAgents,
    DiscoverSliders,
    Network,
    Metadata,
    Cache,
    Logs,
    About,
}

/**
 * The main settings form. A null field is one this server's lineage does not have, and the form
 * leaves it out: Overseerr has one region and the proxy switches; the Jellyseerr lineage split the
 * region in two and added the streaming, hide-requested, special-episode and YouTube ones.
 */
data class ServerGeneralSettings(
    val applicationTitle: String = "",
    val applicationUrl: String = "",
    val locale: String = "",
    val discoverRegion: String = "",
    val streamingRegion: String? = null,
    val originalLanguage: String = "",
    val hideAvailable: Boolean = false,
    val hideRequested: Boolean? = null,
    /** Null where the server has no blocklist; it hides blocklisted titles for viewers who manage the blocklist. */
    val hideBlocklisted: Boolean? = null,
    val partialRequests: Boolean = true,
    val specialEpisodes: Boolean? = null,
    val versionCheck: Boolean? = null,
    val cacheImages: Boolean = false,
    val youtubeUrl: String? = null,
    val trustProxy: Boolean? = null,
    val csrfProtection: Boolean? = null,
    /** The automatic blocklist's settings; null on a server without them. */
    val blocklist: BlocklistSettings? = null,
) {
    /** A blank URL clears it; anything else has to be a web address the server can serve links from. */
    val urlValid: Boolean get() = applicationUrl.isBlank() || applicationUrl.trim().isWebUrl()

    /** Blank is "use the default"; anything else has to have the shape of its code. */
    val localeValid: Boolean get() = locale.isBlank() || LanguageCodeShapes.isLocale(locale)
    val discoverRegionValid: Boolean get() = discoverRegion.isBlank() || LanguageCodeShapes.isRegion(discoverRegion)
    val streamingRegionValid: Boolean get() = streamingRegion.isNullOrBlank() || LanguageCodeShapes.isRegion(streamingRegion)
    val originalLanguageValid: Boolean get() = originalLanguage.isBlank() || LanguageCodeShapes.isOriginalLanguage(originalLanguage)

    val valid: Boolean
        get() = urlValid && localeValid && discoverRegionValid && streamingRegionValid && originalLanguageValid && blocklist?.valid != false
}

/**
 * What the "Process Blocklisted Tags" job reads: the region and languages it scans, apart from Discover's, the TMDB
 * keywords whose titles it blocklists (ids, comma-separated, as the server keeps them), and how many pages it takes per
 * tag, which the web client holds to 0 through 250.
 */
data class BlocklistSettings(
    val region: String = "",
    val languages: String = "",
    val tags: String = "",
    val tagsLimit: String = DEFAULT_TAGS_LIMIT.toString(),
) {
    val tagIds: List<Int> get() = tags.split(',').mapNotNull { it.trim().toIntOrNull() }

    val tagsLimitValid: Boolean get() = tagsLimit.trim().toIntOrNull()?.let { it in 0..MAX_TAGS_LIMIT } == true

    val valid: Boolean get() = tagsLimitValid
}

/** The web client's starting tag limit, and its ceiling. */
internal const val DEFAULT_TAGS_LIMIT = 50
internal const val MAX_TAGS_LIMIT = 250

/** The server's API key beside the form: shown only on request, and replaceable. */
data class ApiKeyState(
    val key: String = "",
    val revealed: Boolean = false,
    val regenerating: Boolean = false,
)

/** What the general page shows beside its form; both wait on the same load. */
data class ServerGeneralExtras(
    val apiKey: ApiKeyState = ApiKeyState(),
    /** Which fork this is, for the display languages it ships: Overseerr's are fewer than the Jellyseerr lineage's. */
    val variant: SeerrVariant = SeerrVariant.Unknown,
    /** The server's lists, each read only once its picker opens; absent until then. */
    val lists: Map<ServerList, ListChoices> = emptyMap(),
    /** The blocklisted tags' names and the keyword search, read only while the tags picker is open. */
    val keywords: KeywordSearch = KeywordSearch(),
)

/** One TMDB keyword, as the tags picker lists it. */
data class Keyword(
    val id: Int,
    val name: String,
)

/**
 * The tags picker's keywords: [names] for the ids the server holds, read once the picker opens, and [results] for the
 * last search, null before one. [failed] is a search that could not be read.
 */
data class KeywordSearch(
    val names: Map<Int, String> = emptyMap(),
    val results: List<Keyword>? = null,
    val searching: Boolean = false,
    val failed: Boolean = false,
)

internal fun SeerrMainSettingsDto.toServerGeneral(variant: SeerrVariant): ServerGeneralSettings {
    val lineage = variant.isJellyseerrLineage
    val overseerr = variant == SeerrVariant.Overseerr
    return ServerGeneralSettings(
        applicationTitle = applicationTitle.orEmpty(),
        applicationUrl = applicationUrl.orEmpty(),
        locale = locale.orEmpty(),
        discoverRegion = (discoverRegion ?: region).orEmpty(),
        streamingRegion = streamingRegion.orEmpty().takeIf { lineage },
        originalLanguage = originalLanguage.orEmpty(),
        hideAvailable = hideAvailable ?: false,
        hideRequested = (hideRequested ?: false).takeIf { lineage },
        hideBlocklisted = hideBlocklisted,
        partialRequests = partialRequestsEnabled ?: true,
        specialEpisodes = (enableSpecialEpisodes ?: false).takeIf { lineage },
        versionCheck = versionCheck,
        cacheImages = cacheImages ?: false,
        youtubeUrl = youtubeUrl.orEmpty().takeIf { lineage },
        trustProxy = (trustProxy ?: false).takeIf { overseerr },
        csrfProtection = (csrfProtection ?: false).takeIf { overseerr },
        blocklist = toBlocklist(),
    )
}

/** The server sends every main setting it has, so a server whose answer has none of these has no automatic blocklist. */
private fun SeerrMainSettingsDto.toBlocklist(): BlocklistSettings? =
    if (listOf(blocklistRegion, blocklistLanguage, blocklistedTags, blocklistedTagsLimit).all { it == null }) {
        null
    } else {
        BlocklistSettings(
            region = blocklistRegion.orEmpty(),
            languages = blocklistLanguage.orEmpty(),
            tags = blocklistedTags.orEmpty(),
            tagsLimit = (blocklistedTagsLimit ?: DEFAULT_TAGS_LIMIT).toString(),
        )
    }

/**
 * Only what the form holds; a field the lineage lacks stays null and is left out. The region goes
 * under both names, since Jellyseerr 1.x still read `region` before the split.
 */
internal fun ServerGeneralSettings.toBody(): SeerrMainSettingsUpdateBody =
    SeerrMainSettingsUpdateBody(
        applicationTitle = applicationTitle.trim(),
        applicationUrl = applicationUrl.trim().trimEnd('/'),
        locale = locale.trim(),
        region = discoverRegion.trim(),
        discoverRegion = discoverRegion.trim().takeIf { streamingRegion != null },
        streamingRegion = streamingRegion?.trim(),
        originalLanguage = originalLanguage.trim(),
        hideAvailable = hideAvailable,
        hideRequested = hideRequested,
        hideBlocklisted = hideBlocklisted,
        blocklistRegion = blocklist?.region?.trim(),
        blocklistLanguage = blocklist?.languages?.trim(),
        blocklistedTags = blocklist?.tagIds?.joinToString(","),
        blocklistedTagsLimit = blocklist?.tagsLimit?.trim()?.toIntOrNull(),
        partialRequestsEnabled = partialRequests,
        enableSpecialEpisodes = specialEpisodes,
        versionCheck = versionCheck,
        cacheImages = cacheImages,
        youtubeUrl = youtubeUrl?.trim(),
        trustProxy = trustProxy,
        csrfProtection = csrfProtection,
    )
