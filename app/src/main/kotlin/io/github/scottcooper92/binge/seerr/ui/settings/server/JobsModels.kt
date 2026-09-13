package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrApiCacheDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrCacheDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrDnsEntryDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrJobDto
import io.github.scottcooper92.binge.seerr.ui.settings.toEpochMillisOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** How often a job runs by design: [Fixed] ones keep the server's own interval and offer no schedule. */
enum class JobInterval { Short, Long, Fixed }

data class ServerJob(
    val id: String,
    val name: String,
    val interval: JobInterval?,
    val running: Boolean,
    val nextRunMillis: Long?,
) {
    val schedulable: Boolean get() = interval != null && interval != JobInterval.Fixed
}

/**
 * The schedules the web client offers, as the six-field cron the server takes (seconds first).
 * A [JobInterval.Short] job picks from minutes, a [JobInterval.Long] one from hours and days.
 */
data class SchedulePreset(
    val cron: String,
    val every: Int,
    val unit: ScheduleUnit,
)

enum class ScheduleUnit { Minutes, Hours, Days }

val MINUTE_PRESETS: List<SchedulePreset> = listOf(5, 10, 15, 30).map { SchedulePreset("0 */$it * * * *", it, ScheduleUnit.Minutes) }

val HOUR_PRESETS: List<SchedulePreset> =
    listOf(1, 2, 4, 6, 8, 12).map { SchedulePreset("0 0 */$it * * *", it, ScheduleUnit.Hours) } +
        listOf(1, 2, 3, 7).map { SchedulePreset("0 0 0 */$it * *", it, ScheduleUnit.Days) }

fun ServerJob.presets(): List<SchedulePreset> =
    when (interval) {
        JobInterval.Short -> MINUTE_PRESETS
        JobInterval.Long -> HOUR_PRESETS
        else -> emptyList()
    }

sealed interface JobsUiState {
    data object Loading : JobsUiState

    data class Error(
        val error: SeerrError,
    ) : JobsUiState

    /** [busyIds] are the jobs with a run, cancel or schedule in flight. */
    data class Ready(
        val jobs: List<ServerJob>,
        val busyIds: Set<String> = emptySet(),
    ) : JobsUiState
}

data class ApiCache(
    val id: String,
    val name: String,
    val hits: Long,
    val misses: Long,
    val keys: Long,
)

data class ImageCache(
    val name: String,
    val bytes: Long,
    val imageCount: Long,
)

data class DnsEntry(
    val hostname: String,
    val activeAddress: String?,
    val ttlSeconds: Long?,
    val hits: Long,
    val misses: Long,
)

data class DnsCache(
    val size: Long,
    val maxSize: Long,
    val hits: Long,
    val misses: Long,
    val failures: Long,
    val entries: List<DnsEntry>,
)

sealed interface CacheUiState {
    data object Loading : CacheUiState

    data class Error(
        val error: SeerrError,
    ) : CacheUiState

    /** [dns] is null where the server has no DNS cache (before Seerr 3); [busyIds] are the flushes in flight. */
    data class Ready(
        val apiCaches: List<ApiCache>,
        val imageCaches: List<ImageCache>,
        val dns: DnsCache?,
        val busyIds: Set<String> = emptySet(),
    ) : CacheUiState
}

internal fun SeerrJobDto.toServerJob(): ServerJob =
    ServerJob(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: id,
        interval =
            when (interval) {
                "short" -> JobInterval.Short
                "long" -> JobInterval.Long
                "fixed" -> JobInterval.Fixed
                else -> null
            },
        running = running,
        nextRunMillis = nextExecutionTime?.toEpochMillisOrNull(),
    )

internal fun SeerrApiCacheDto.toApiCache(): ApiCache =
    ApiCache(id = id, name = name?.takeIf { it.isNotBlank() } ?: id, hits = stats.hits, misses = stats.misses, keys = stats.keys)

internal fun SeerrCacheDto.toReady(): CacheUiState.Ready =
    CacheUiState.Ready(
        apiCaches = apiCaches.map { it.toApiCache() },
        imageCaches = imageCache.map { (name, cache) -> ImageCache(name = name, bytes = cache.size, imageCount = cache.imageCount) },
        dns =
            dnsCache?.let { dns ->
                DnsCache(
                    size = dns.stats.size,
                    maxSize = dns.stats.maxSize,
                    hits = dns.stats.hits,
                    misses = dns.stats.misses,
                    failures = dns.stats.failures,
                    entries = dns.entries.toDnsEntries(),
                )
            },
    )

/** The server keys entries by hostname; a list carrying the hostname inside each entry is read too. */
private fun JsonElement?.toDnsEntries(): List<DnsEntry> {
    val json = Json { ignoreUnknownKeys = true }

    fun JsonObject.entry(hostname: String?): DnsEntry? {
        val dto = runCatching { json.decodeFromJsonElement(SeerrDnsEntryDto.serializer(), this) }.getOrNull() ?: return null
        val host = hostname ?: dto.hostname ?: return null
        return DnsEntry(hostname = host, activeAddress = dto.activeAddress, ttlSeconds = dto.ttl, hits = dto.hits, misses = dto.misses)
    }
    return when (this) {
        is JsonObject -> mapNotNull { (host, value) -> (value as? JsonObject)?.entry(host) }
        is JsonArray -> mapNotNull { (it as? JsonObject)?.entry(null) }
        else -> emptyList()
    }.sortedBy { it.hostname }
}
