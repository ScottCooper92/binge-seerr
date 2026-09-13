package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET settings/main`, admin-only. The API key the server returns beside these is deliberately not
 * a field: nothing here should hold it, let alone show it.
 */
@Serializable
data class SeerrMainSettingsDto(
    @SerialName("applicationTitle") val applicationTitle: String? = null,
    @SerialName("applicationUrl") val applicationUrl: String? = null,
    @SerialName("appLanguage") val appLanguage: String? = null,
    @SerialName("hideAvailable") val hideAvailable: Boolean? = null,
    @SerialName("defaultPermissions") val defaultPermissions: Int? = null,
    @SerialName("defaultQuotas") val defaultQuotas: SeerrDefaultQuotasDto? = null,
)

@Serializable
data class SeerrDefaultQuotasDto(
    @SerialName("movie") val movie: SeerrDefaultQuotaDto? = null,
    @SerialName("tv") val tv: SeerrDefaultQuotaDto? = null,
)

@Serializable
data class SeerrDefaultQuotaDto(
    @SerialName("quotaLimit") val quotaLimit: Int? = null,
    @SerialName("quotaDays") val quotaDays: Int? = null,
)

/** `GET settings/about`, admin-only: the build and the lifetime totals. */
@Serializable
data class SeerrAboutDto(
    @SerialName("version") val version: String? = null,
    @SerialName("totalRequests") val totalRequests: Int? = null,
    @SerialName("totalMediaItems") val totalMediaItems: Int? = null,
)

/** One scheduled job (`GET settings/jobs`); [nextExecutionTime] is an ISO timestamp. */
@Serializable
data class SeerrJobDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("interval") val interval: String? = null,
    @SerialName("nextExecutionTime") val nextExecutionTime: String? = null,
    @SerialName("running") val running: Boolean = false,
)

/** A notification agent's settings (`GET settings/notifications/{agent}`); only whether it is on is read. */
@Serializable
data class SeerrNotificationAgentDto(
    @SerialName("enabled") val enabled: Boolean = false,
)

/** A Radarr or Sonarr instance as the admin configured it (`GET settings/radarr`, `settings/sonarr`). */
@Serializable
data class SeerrServiceSettingsDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("useSsl") val useSsl: Boolean = false,
    @SerialName("baseUrl") val baseUrl: String? = null,
    @SerialName("activeProfileName") val activeProfileName: String? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("externalUrl") val externalUrl: String? = null,
)
