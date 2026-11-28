package com.cooper.binge.companion.seerr.ui.settings.server

import com.binge.companion.sdk.paging.OffsetPage
import com.binge.companion.sdk.paging.OffsetPagingSource
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrLogEntryDto
import com.cooper.binge.companion.seerr.seerr.SeerrLogPageDto
import com.cooper.binge.companion.seerr.seerr.toEpochMillisOrNull
import com.cooper.homelab.core.presentation.diagnostics.ServerLogEntry
import com.cooper.homelab.core.presentation.diagnostics.ServerLogLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

const val LOGS_PAGE_SIZE = 25

/** The level as the server's log API names it; the filter shows a level and everything above it. */
val ServerLogLevel.apiValue: String
    get() = name.lowercase()

/** The level a log API value names; a value the server sends that this app does not know reads as Info. */
fun serverLogLevelFromApi(value: String?): ServerLogLevel =
    ServerLogLevel.entries.firstOrNull { it.apiValue == value?.lowercase() } ?: ServerLogLevel.Info

/** What copying a line puts on the clipboard: the line as the server's log file has it. */
val ServerLogEntry.copyText: String
    get() = listOfNotNull(timestampRaw, "[${level.apiValue}]", label?.let { "[$it]" }, message, data).joinToString(" ")

/**
 * Flat, unlike the sealed states elsewhere, because it holds only the query — the level and the
 * search, both always editable. Loading and failure belong to the paging flow `entries`, which is
 * where a log page is actually read.
 */
data class LogsUiState(
    val level: ServerLogLevel = ServerLogLevel.Info,
    val search: String = "",
)

/** What the logs page is told to do rather than shown: while following, re-read the newest lines. */
sealed interface LogsEvent {
    data object Refresh : LogsEvent
}

/** Pages the log; the server answers `{pageInfo, results}`, and a bare array (the spec's shape) is read as one page. */
class LogsPagingSource(
    private val api: suspend () -> SeerrApi,
    private val level: ServerLogLevel,
    private val search: String?,
) : OffsetPagingSource<ServerLogEntry>(LOGS_PAGE_SIZE) {
    override suspend fun loadPage(
        take: Int,
        skip: Int,
    ): OffsetPage<ServerLogEntry> {
        val answer = api().logs(take = take, skip = skip, filter = level.apiValue, search = search?.takeIf { it.isNotBlank() })
        val page = answer.toLogPage()
        return OffsetPage(
            page.results.mapIndexed {
                index,
                dto,
                ->
                dto.toServerLogEntry(skip + index)
            },
            page.pageInfo.pages.takeIf { it > 0 },
        )
    }
}

private val json =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

fun JsonElement.toLogPage(): SeerrLogPageDto =
    when (this) {
        is JsonObject -> json.decodeFromJsonElement(serializer<SeerrLogPageDto>(), this)
        is JsonArray -> SeerrLogPageDto(results = map { json.decodeFromJsonElement(serializer<SeerrLogEntryDto>(), it) })
        else -> SeerrLogPageDto()
    }

fun SeerrLogEntryDto.toServerLogEntry(index: Int): ServerLogEntry =
    ServerLogEntry(
        id = "$index:${timestamp.orEmpty()}",
        timestampMillis = timestamp?.toEpochMillisOrNull(),
        timestampRaw = timestamp.orEmpty(),
        level = serverLogLevelFromApi(level),
        label = label?.takeIf { it.isNotBlank() },
        message = message.orEmpty(),
        data = data?.takeIf { it !is JsonObject || it.isNotEmpty() }?.let { json.encodeToString(serializer<JsonElement>(), it) },
    )
