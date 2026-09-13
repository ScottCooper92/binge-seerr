package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrDvrTestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrDvrTestResultDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrOverrideRuleDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServiceSettingsDto
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType

private const val PORT_MAX = 65_535
private const val RADARR_PORT = "7878"
private const val SONARR_PORT = "8989"

/** The path segment Seerr keys an instance's routes on. */
val ServiceType.apiSegment: String get() = if (this == ServiceType.Radarr) "radarr" else "sonarr"

/** Radarr's minimum availability, as the server spells it. */
val MINIMUM_AVAILABILITIES: List<String> = listOf("announced", "inCinemas", "released")

/** Sonarr's series types, as the server spells them. */
val SERIES_TYPES: List<String> = listOf("standard", "daily", "anime")

/** Sonarr's "Monitor New Seasons" default: the web client's own default for a new instance. */
private const val MONITOR_NEW_ITEMS_ALL = "all"

/** One instance as the services page lists it. */
data class DvrSummary(
    val id: Int,
    val type: ServiceType,
    val name: String,
    val address: String,
    val is4k: Boolean,
    val isDefault: Boolean,
)

/** One override rule as the services page lists it: which instance, and how many conditions it carries. */
data class OverrideRuleSummary(
    val id: Int,
    val instanceName: String,
    val conditions: Int,
)

sealed interface ServicesUiState {
    data object Loading : ServicesUiState

    data class Error(
        val error: SeerrError,
    ) : ServicesUiState

    /** [rules] is null where the server has no override rules (Overseerr, Jellyseerr before 2.2). */
    data class Ready(
        val instances: List<DvrSummary>,
        val rules: List<OverrideRuleSummary>?,
    ) : ServicesUiState
}

/**
 * The instance form. The connection half is what a test needs; the destination half — profile,
 * folder, tags — is picked from what the test answered, so it is empty until one has run. The
 * Radarr-only and Sonarr-only fields are null on the other kind.
 */
data class DvrForm(
    val type: ServiceType,
    val id: Int? = null,
    val name: String = "",
    val host: String = "",
    val port: String = "",
    val useSsl: Boolean = false,
    val apiKey: String = "",
    val baseUrl: String = "",
    val externalUrl: String = "",
    val is4k: Boolean = false,
    val isDefault: Boolean = false,
    val syncEnabled: Boolean = false,
    val preventSearch: Boolean = false,
    val tagRequests: Boolean = false,
    val profileId: Int? = null,
    val profileName: String? = null,
    val rootFolder: String? = null,
    val tagIds: Set<Int> = emptySet(),
    val minimumAvailability: String? = null,
    val seriesType: String? = null,
    val animeSeriesType: String? = null,
    val animeProfileId: Int? = null,
    val animeProfileName: String? = null,
    val animeRootFolder: String? = null,
    val animeTagIds: Set<Int>? = null,
    val seasonFolders: Boolean? = null,
    val languageProfileId: Int? = null,
    val monitorNewItems: String? = null,
) {
    val connectionValid: Boolean
        get() = host.isNotBlank() && port.trim().toIntOrNull()?.let { it in 1..PORT_MAX } == true && apiKey.isNotBlank()

    val valid: Boolean get() = connectionValid && name.isNotBlank() && profileId != null && !rootFolder.isNullOrBlank()

    companion object {
        /** What a new instance starts as: the kind's usual port, and Sonarr's season folders on. */
        fun blank(type: ServiceType): DvrForm =
            when (type) {
                ServiceType.Radarr -> DvrForm(type = type, port = RADARR_PORT, minimumAvailability = MINIMUM_AVAILABILITIES.last())
                ServiceType.Sonarr ->
                    DvrForm(
                        type = type,
                        port = SONARR_PORT,
                        seriesType = SERIES_TYPES.first(),
                        animeSeriesType = SERIES_TYPES.first(),
                        animeTagIds = emptySet(),
                        seasonFolders = true,
                        monitorNewItems = MONITOR_NEW_ITEMS_ALL,
                    )
            }
    }
}

/** What a reached instance offers; [languageProfiles] only from a Sonarr 3. */
data class DvrChoices(
    val profiles: List<Choice>,
    val rootFolders: List<String>,
    val tags: List<Choice>,
    val languageProfiles: List<Choice>?,
)

data class DvrExtras(
    val choices: DvrChoices? = null,
    val testing: Boolean = false,
)

/**
 * The rule form. The conditions are what the server matches a new request against; the overrides
 * are what it changes. Users are picked by id; genres and keywords are TMDB ids and languages ISO
 * codes, typed as the web client takes them.
 */
data class OverrideRuleForm(
    val id: Int? = null,
    val serviceType: ServiceType? = null,
    val serviceId: Int? = null,
    val userIds: Set<Int> = emptySet(),
    val genres: String = "",
    val languages: String = "",
    val keywords: String = "",
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val tagIds: Set<Int> = emptySet(),
) {
    val valid: Boolean get() = serviceId != null && serviceType != null
}

/** What the rule page picks from: every instance, the users, and the chosen instance's own choices. */
data class OverrideRuleExtras(
    val instances: List<DvrSummary> = emptyList(),
    val users: List<Choice> = emptyList(),
    val choices: DvrChoices? = null,
    val loadingChoices: Boolean = false,
)

internal fun SeerrServiceSettingsDto.toSummary(type: ServiceType): DvrSummary? {
    val id = id ?: return null
    return DvrSummary(
        id = id,
        type = type,
        name = name?.takeIf { it.isNotBlank() } ?: type.name,
        address = listOfNotNull(hostname, port?.toString()).joinToString(":"),
        is4k = is4k,
        isDefault = isDefault,
    )
}

internal fun SeerrServiceSettingsDto.toForm(type: ServiceType): DvrForm =
    DvrForm(
        type = type,
        id = id,
        name = name.orEmpty(),
        host = hostname.orEmpty(),
        port = port?.toString().orEmpty(),
        useSsl = useSsl,
        apiKey = apiKey.orEmpty(),
        baseUrl = baseUrl.orEmpty(),
        externalUrl = externalUrl.orEmpty(),
        is4k = is4k,
        isDefault = isDefault,
        syncEnabled = syncEnabled,
        preventSearch = preventSearch,
        tagRequests = tagRequests,
        profileId = activeProfileId,
        profileName = activeProfileName,
        rootFolder = activeDirectory,
        tagIds = tags.toSet(),
        minimumAvailability = if (type == ServiceType.Radarr) minimumAvailability ?: MINIMUM_AVAILABILITIES.last() else null,
        seriesType = if (type == ServiceType.Sonarr) seriesType ?: SERIES_TYPES.first() else null,
        animeSeriesType = if (type == ServiceType.Sonarr) animeSeriesType ?: SERIES_TYPES.first() else null,
        animeProfileId = activeAnimeProfileId,
        animeProfileName = activeAnimeProfileName,
        animeRootFolder = activeAnimeDirectory,
        animeTagIds = if (type == ServiceType.Sonarr) animeTags.orEmpty().toSet() else null,
        seasonFolders = if (type == ServiceType.Sonarr) enableSeasonFolders ?: true else null,
        languageProfileId = activeLanguageProfileId,
        monitorNewItems = if (type == ServiceType.Sonarr) monitorNewItems ?: MONITOR_NEW_ITEMS_ALL else null,
    )

/**
 * The record the server stores; the profile names ride along because the server shows them
 * without asking the instance. A fresh test's answer is preferred, but where the instance wasn't
 * (re)tested this session — an existing record, loaded while its instance was briefly unreachable
 * — the name already on the record is kept rather than blanked.
 */
internal fun DvrForm.toDto(choices: DvrChoices?): SeerrServiceSettingsDto =
    SeerrServiceSettingsDto(
        id = id,
        name = name.trim(),
        hostname = host.trim(),
        port = port.trim().toInt(),
        apiKey = apiKey.trim(),
        useSsl = useSsl,
        baseUrl = baseUrl.trim().takeIf { it.isNotEmpty() },
        activeProfileId = profileId,
        activeProfileName = choices?.profiles?.firstOrNull { it.id == profileId }?.label ?: profileName,
        activeDirectory = rootFolder,
        tags = tagIds.toList(),
        is4k = is4k,
        isDefault = isDefault,
        externalUrl = externalUrl.trim().takeIf { it.isNotEmpty() },
        syncEnabled = syncEnabled,
        preventSearch = preventSearch,
        tagRequests = tagRequests,
        minimumAvailability = minimumAvailability,
        seriesType = seriesType,
        animeSeriesType = animeSeriesType,
        activeAnimeProfileId = animeProfileId,
        activeAnimeProfileName = choices?.profiles?.firstOrNull { it.id == animeProfileId }?.label ?: animeProfileName,
        activeAnimeDirectory = animeRootFolder,
        animeTags = animeTagIds?.toList(),
        enableSeasonFolders = seasonFolders,
        activeLanguageProfileId = languageProfileId,
        monitorNewItems = monitorNewItems,
    )

internal fun DvrForm.toTestBody(): SeerrDvrTestBody =
    SeerrDvrTestBody(
        hostname = host.trim(),
        port = port.trim().toInt(),
        useSsl = useSsl,
        baseUrl = baseUrl.trim().takeIf { it.isNotEmpty() },
        apiKey = apiKey.trim(),
    )

internal fun SeerrDvrTestResultDto.toChoices(): DvrChoices =
    DvrChoices(
        profiles = profiles.map { Choice(it.id, it.name) },
        rootFolders = rootFolders.map { it.path },
        tags = tags.map { Choice(it.id, it.label) },
        languageProfiles = languageProfiles?.map { Choice(it.id, it.name) },
    )

internal fun SeerrOverrideRuleDto.toForm(): OverrideRuleForm =
    OverrideRuleForm(
        id = id,
        serviceType =
            when {
                radarrServiceId != null -> ServiceType.Radarr
                sonarrServiceId != null -> ServiceType.Sonarr
                else -> null
            },
        serviceId = radarrServiceId ?: sonarrServiceId,
        userIds = users.toIdSet(),
        genres = genre.orEmpty(),
        languages =
            language
                .orEmpty()
                .split('|')
                .filter { it.isNotBlank() }
                .joinToString(", "),
        keywords = keywords.orEmpty(),
        profileId = profileId,
        rootFolder = rootFolder?.takeIf { it.isNotBlank() },
        tagIds = tags.toIdSet(),
    )

/** The server's own encoding: ids comma-joined, language codes pipe-joined; an empty condition is left out. */
internal fun OverrideRuleForm.toDto(): SeerrOverrideRuleDto =
    SeerrOverrideRuleDto(
        radarrServiceId = serviceId.takeIf { serviceType == ServiceType.Radarr },
        sonarrServiceId = serviceId.takeIf { serviceType == ServiceType.Sonarr },
        users = userIds.sorted().joinToString(",").takeIf { it.isNotEmpty() },
        genre = genres.toIdList(),
        language =
            languages
                .split(',', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("|")
                .takeIf { it.isNotEmpty() },
        keywords = keywords.toIdList(),
        profileId = profileId,
        rootFolder = rootFolder,
        tags = tagIds.sorted().joinToString(",").takeIf { it.isNotEmpty() },
    )

private fun String?.toIdSet(): Set<Int> = orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

private fun String.toIdList(): String? = split(',').mapNotNull { it.trim().toIntOrNull() }.joinToString(",").takeIf { it.isNotEmpty() }
