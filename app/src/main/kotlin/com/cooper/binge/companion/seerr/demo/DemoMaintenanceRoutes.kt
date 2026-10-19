package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrApiCacheDto
import com.cooper.binge.companion.seerr.seerr.SeerrCacheDto
import com.cooper.binge.companion.seerr.seerr.SeerrCacheStatsDto
import com.cooper.binge.companion.seerr.seerr.SeerrDnsCacheDto
import com.cooper.binge.companion.seerr.seerr.SeerrImageCacheDto
import com.cooper.binge.companion.seerr.seerr.SeerrJobDto
import com.cooper.binge.companion.seerr.seerr.SeerrJobScheduleBody
import com.cooper.binge.companion.seerr.seerr.SeerrLogEntryDto
import com.cooper.binge.companion.seerr.seerr.SeerrLogPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrNotificationAgentDto
import com.cooper.binge.companion.seerr.seerr.SeerrPushoverSoundDto
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.Instant

/** Jobs, caches, the log and the notification agents: what the maintenance pages read, and the actions on them. */
internal fun DemoData.maintenanceRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "settings", "jobs") { _, _ -> ok(jobs) },
        route("POST", "settings", "jobs", "{}", "run") { _, (id) -> withJob(id) { ok(it) } },
        route("POST", "settings", "jobs", "{}", "cancel") { _, (id) -> withJob(id) { ok(it) } },
        route("POST", "settings", "jobs", "{}", "schedule") { call, (id) -> withJob(id) { reschedule(it, call.body) } },
        route("GET", "settings", "cache") { _, _ -> ok(caches()) },
        route("POST", "settings", "cache", "{}", "flush") { _, _ -> done },
        route("POST", "settings", "cache", "dns", "{}", "flush") { _, _ -> done },
        route("GET", "settings", "logs") { call, _ -> jsonAnswer(logs(call)) },
        route(
            "GET",
            "settings",
            "notifications",
            "pushover",
            "sounds",
        ) { _, _ -> ok(listOf(SeerrPushoverSoundDto("pushover", "Pushover"))) },
        route("GET", "settings", "notifications", "{}") { _, (agent) -> ok(agentOf(agent)) },
        route("POST", "settings", "notifications", "{}", "test") { _, _ -> done },
        route("POST", "settings", "notifications", "{}") { call, (agent) -> saveAgent(agent, call.body) },
    )

private fun DemoData.withJob(
    id: String,
    block: (SeerrJobDto) -> DemoAnswer,
): DemoAnswer = jobs.firstOrNull { it.id == id }?.let(block) ?: demoNotFound

/** Any schedule the app sends is kept as "custom": the demo does not run jobs, so only what the page then reads matters. */
private fun DemoData.reschedule(
    job: SeerrJobDto,
    body: String,
): DemoAnswer {
    demoJson.decodeFromString<SeerrJobScheduleBody>(body)
    val next = job.copy(interval = "custom", nextExecutionTime = now().plus(Duration.ofMinutes(RESCHEDULE_MINUTES)).toString())
    jobs[jobs.indexOf(job)] = next
    return ok(next)
}

private const val RESCHEDULE_MINUTES = 5L

private fun DemoData.agentOf(agent: String): SeerrNotificationAgentDto =
    resources.objects["agent-$agent"]?.let { demoJson.decodeFromJsonElement(SeerrNotificationAgentDto.serializer(), it) }
        ?: SeerrNotificationAgentDto()

private fun DemoData.saveAgent(
    agent: String,
    body: String,
): DemoAnswer {
    resources.objects["agent-$agent"] = parseObject(body)
    return ok(agentOf(agent))
}

private fun caches() =
    SeerrCacheDto(
        apiCaches =
            listOf(
                SeerrApiCacheDto("tmdb", "TMDB API", SeerrCacheStatsDto(hits = 420, misses = 36, keys = 36)),
                SeerrApiCacheDto("github", "GitHub API", SeerrCacheStatsDto(hits = 12, misses = 2, keys = 2)),
            ),
        imageCache = mapOf("tmdb" to SeerrImageCacheDto(size = 0, imageCount = 0)),
        dnsCache = SeerrDnsCacheDto(),
    )

private fun DemoData.logs(call: DemoCall): JsonObject {
    val level = call.query("filter") ?: "debug"
    val search = call.query("search")?.takeIf { it.isNotBlank() }
    val rows =
        logLines(now()).filter {
            (level == "debug" || it.level == level) &&
                (search == null || it.message.orEmpty().contains(search, true))
        }
    val (page, info) = rows.pageFor(call)
    return toJson(SeerrLogPageDto(pageInfo = info, results = page))
}

private fun logLines(now: Instant) =
    listOf(
        logLine("API", "info", "Demo server started.", now, minutesAgo = 120),
        logLine("Jobs", "info", "Availability sync finished.", now, minutesAgo = 45),
        logLine("Notifications", "warn", "No notification agent is enabled.", now, minutesAgo = 30),
        logLine("Download Sync", "debug", "Checking the download queue.", now, minutesAgo = 5),
    )

private fun logLine(
    label: String,
    level: String,
    message: String,
    now: Instant,
    minutesAgo: Long,
) = SeerrLogEntryDto(label, level, message, now.minus(Duration.ofMinutes(minutesAgo)).toString())
