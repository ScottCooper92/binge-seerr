package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrLogEntryDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrLogPageDto
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPage
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPagingSource
import io.github.scottcooper92.binge.seerr.ui.settings.toEpochMillisOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val LOGS_PAGE_SIZE = 25

/** The server's log levels, lowest first; the filter shows a level and everything above it. */
enum class LogLevel(
    val apiValue: String,
) {
    Debug("debug"),
    Info("info"),
    Warn("warn"),
    Error("error"),
    ;

    companion object {
        fun fromApi(value: String?): LogLevel = entries.firstOrNull { it.apiValue == value?.lowercase() } ?: Info
    }
}

/** One log line; [data] is the attached object pretty-printed, shown when the line is expanded. */
data class LogEntry(
    val id: String,
    val timestampMillis: Long?,
    val timestampRaw: String,
    val level: LogLevel,
    val label: String?,
    val message: String,
    val data: String?,
) {
    /** What copying a line puts on the clipboard: the line as the server's log file has it. */
    val copyText: String
        get() = listOfNotNull(timestampRaw, "[${level.apiValue}]", label?.let { "[$it]" }, message, data).joinToString(" ")
}

data class LogsUiState(
    val level: LogLevel = LogLevel.Info,
    val search: String = "",
)

/** Pages the log; the server answers `{pageInfo, results}`, and a bare array (the spec's shape) is read as one page. */
class LogsPagingSource(
    private val api: suspend () -> SeerrApi,
    private val level: LogLevel,
    private val search: String?,
) : OffsetPagingSource<LogEntry>(LOGS_PAGE_SIZE) {
    override suspend fun loadPage(
        take: Int,
        skip: Int,
    ): OffsetPage<LogEntry> {
        val answer = api().logs(take = take, skip = skip, filter = level.apiValue, search = search?.takeIf { it.isNotBlank() })
        val page = answer.toLogPage()
        return OffsetPage(page.results.mapIndexed { index, dto -> dto.toLogEntry(skip + index) }, page.pageInfo.pages.takeIf { it > 0 })
    }
}

private val json =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

internal fun JsonElement.toLogPage(): SeerrLogPageDto =
    when (this) {
        is JsonObject -> json.decodeFromJsonElement(SeerrLogPageDto.serializer(), this)
        is JsonArray -> SeerrLogPageDto(results = map { json.decodeFromJsonElement(SeerrLogEntryDto.serializer(), it) })
        else -> SeerrLogPageDto()
    }

internal fun SeerrLogEntryDto.toLogEntry(index: Int): LogEntry =
    LogEntry(
        id = "$index:${timestamp.orEmpty()}",
        timestampMillis = timestamp?.toEpochMillisOrNull(),
        timestampRaw = timestamp.orEmpty(),
        level = LogLevel.fromApi(level),
        label = label?.takeIf { it.isNotBlank() },
        message = message.orEmpty(),
        data = data?.takeIf { it !is JsonObject || it.isNotEmpty() }?.let { json.encodeToString(JsonElement.serializer(), it) },
    )
