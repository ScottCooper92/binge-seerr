package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The metadata providers (`settings/metadatas`, Seerr 3.0+): which of TMDB and TVDB serves series
 * and anime. Both fields sit at the top level of the body and of the answer, not under a wrapper —
 * the server reads `req.body.tv` and `req.body.anime`, and writes whatever it read, so a key it
 * cannot see is a provider cleared rather than a provider left alone.
 */
@Serializable
data class SeerrMetadataSettingsDto(
    @SerialName("tv") val tv: String? = null,
    @SerialName("anime") val anime: String? = null,
)

/** `POST settings/metadatas/test`: which providers to reach. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrMetadataTestBody(
    @EncodeDefault @SerialName("tmdb") val tmdb: Boolean = false,
    @EncodeDefault @SerialName("tvdb") val tvdb: Boolean = false,
)

/**
 * `POST settings/metadatas/test`: each provider's result, "ok", "failed" or "not tested". The server answers 500 when
 * one failed, with the same body, so the results are read from the error too.
 */
@Serializable
data class SeerrMetadataTestResultDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("tests") val tests: SeerrMetadataTestsDto? = null,
)

@Serializable
data class SeerrMetadataTestsDto(
    @SerialName("tmdb") val tmdb: String? = null,
    @SerialName("tvdb") val tvdb: String? = null,
)

/**
 * The caches (`GET settings/cache`): the API caches with their hit and miss counts, the image
 * caches by name (`tmdb`, and `avatar` on the Jellyseerr lineage), and on Seerr 3 the DNS cache.
 * The DNS entries are kept as sent: the server keys them by hostname, and the shape is read leniently.
 */
@Serializable
data class SeerrCacheDto(
    @SerialName("apiCaches") val apiCaches: List<SeerrApiCacheDto> = emptyList(),
    @SerialName("imageCache") val imageCache: Map<String, SeerrImageCacheDto> = emptyMap(),
    @SerialName("dnsCache") val dnsCache: SeerrDnsCacheDto? = null,
)

@Serializable
data class SeerrApiCacheDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("stats") val stats: SeerrCacheStatsDto = SeerrCacheStatsDto(),
)

@Serializable
data class SeerrCacheStatsDto(
    @SerialName("hits") val hits: Long = 0,
    @SerialName("misses") val misses: Long = 0,
    @SerialName("keys") val keys: Long = 0,
)

@Serializable
data class SeerrImageCacheDto(
    @SerialName("size") val size: Long = 0,
    @SerialName("imageCount") val imageCount: Long = 0,
)

@Serializable
data class SeerrDnsCacheDto(
    @SerialName("stats") val stats: SeerrDnsCacheStatsDto = SeerrDnsCacheStatsDto(),
    @SerialName("entries") val entries: JsonElement? = null,
)

@Serializable
data class SeerrDnsCacheStatsDto(
    @SerialName("size") val size: Long = 0,
    @SerialName("maxSize") val maxSize: Long = 0,
    @SerialName("hits") val hits: Long = 0,
    @SerialName("misses") val misses: Long = 0,
    @SerialName("failures") val failures: Long = 0,
)

/** One DNS cache entry; the hostname is the key it was sent under. */
@Serializable
data class SeerrDnsEntryDto(
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("activeAddress") val activeAddress: String? = null,
    @SerialName("ttl") val ttl: Long? = null,
    @SerialName("hits") val hits: Long = 0,
    @SerialName("misses") val misses: Long = 0,
)

/** One log line (`GET settings/logs`); [data] is whatever the logger attached, kept as sent. */
@Serializable
data class SeerrLogEntryDto(
    @SerialName("label") val label: String? = null,
    @SerialName("level") val level: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("data") val data: JsonElement? = null,
)

/** The logs page: `pageInfo` and `results`, as the server answers despite the spec's bare array. */
@Serializable
data class SeerrLogPageDto(
    @SerialName("pageInfo") val pageInfo: SeerrPageInfoDto = SeerrPageInfoDto(),
    @SerialName("results") val results: List<SeerrLogEntryDto> = emptyList(),
)
