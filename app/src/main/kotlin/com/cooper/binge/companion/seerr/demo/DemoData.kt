package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrDownloadStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrIssueCommentDto
import com.cooper.binge.companion.seerr.seerr.SeerrIssueStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrIssueTypeCode
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import java.time.Duration
import java.time.Instant

private const val ADMIN = 2
private const val LOCAL_USER = 2
private const val DOWNLOAD_BYTES = 4_300_000_000.0
private const val DOWNLOAD_LEFT_BYTES = 1_900_000_000.0
private const val DOWNLOAD_TIME_LEFT = "00:18:00"

/** One request in the demo. [download] is set while it is being fetched, which is what the hub's strip and the request page show. */
internal class DemoRequest(
    val id: Int,
    val title: DemoTitle,
    var status: SeerrRequestStatusCode,
    val requestedBy: Int,
    val createdAt: Instant,
    var updatedAt: Instant,
    var seasons: List<Int> = emptyList(),
    var download: Boolean = false,
    var modifiedBy: Int? = null,
)

internal class DemoIssue(
    val id: Int,
    val title: DemoTitle,
    val type: SeerrIssueTypeCode,
    var status: SeerrIssueStatusCode,
    val createdBy: Int,
    val createdAt: Instant,
    var updatedAt: Instant,
    val comments: MutableList<SeerrIssueCommentDto> = mutableListOf(),
)

internal class DemoBlock(
    val id: Int,
    val title: DemoTitle,
    val blockedBy: Int,
    val createdAt: Instant,
)

/**
 * Everything the demo server holds, seeded for a first look: pending requests, two downloading, a failed one and a declined
 * one, an open issue with a short thread, and three users. [clock] is when "now" is, so the seed reads as recent however
 * long ago the app was built.
 */
internal class DemoData(
    private val clock: () -> Instant,
) {
    val users = seedUsers().toMutableList()
    val requests = mutableListOf<DemoRequest>()
    val issues = mutableListOf<DemoIssue>()
    val blocklist = mutableListOf<DemoBlock>()
    private val mediaOverrides = mutableMapOf<Int, SeerrMediaStatusCode>()
    private var nextRequest = 1
    private var nextIssue = 1
    private var nextComment = 1
    private var nextBlock = 1

    init {
        seed()
    }

    fun now(): Instant = clock()

    fun user(id: Int?): SeerrRequestUserDto? =
        users.firstOrNull { it.id == id }?.let {
            SeerrRequestUserDto(id = it.id, displayName = it.displayName, email = it.email, permissions = it.permissions)
        }

    fun add(
        title: DemoTitle,
        by: Int,
        status: SeerrRequestStatusCode = SeerrRequestStatusCode.Pending,
        seasons: List<Int> = emptyList(),
        agoHours: Long = 0,
    ): DemoRequest {
        val at = now().minus(Duration.ofHours(agoHours))
        return DemoRequest(nextRequest++, title, status, by, at, at, seasons).also { requests += it }
    }

    fun addIssue(
        title: DemoTitle,
        type: SeerrIssueTypeCode,
        by: Int,
        message: String,
        agoHours: Long = 0,
    ): DemoIssue {
        val at = now().minus(Duration.ofHours(agoHours))
        val issue = DemoIssue(nextIssue++, title, type, SeerrIssueStatusCode.Open, by, at, at)
        comment(issue, by, message, agoHours)
        return issue.also { issues += it }
    }

    fun comment(
        issue: DemoIssue,
        by: Int,
        message: String,
        agoHours: Long = 0,
    ): SeerrIssueCommentDto {
        val at = now().minus(Duration.ofHours(agoHours))
        val comment = SeerrIssueCommentDto(nextComment++, message, at.toString(), at.toString(), user(by))
        issue.comments += comment
        issue.updatedAt = at
        return comment
    }

    fun block(
        title: DemoTitle,
        by: Int,
        agoHours: Long = 0,
    ) {
        if (blocklist.none { it.title == title }) blocklist += DemoBlock(nextBlock++, title, by, now().minus(Duration.ofHours(agoHours)))
        requests.removeAll { it.title == title }
        mediaOverrides.remove(title.tmdbId)
    }

    fun unblock(tmdbId: Int) = blocklist.removeAll { it.title.tmdbId == tmdbId }

    fun setMediaStatus(
        title: DemoTitle,
        status: SeerrMediaStatusCode,
    ) {
        mediaOverrides[title.tmdbId] = status
        val done = status == SeerrMediaStatusCode.Available
        requests.filter { it.title == title && it.status == SeerrRequestStatusCode.Approved }.forEach {
            it.download = false
            if (done) it.status = SeerrRequestStatusCode.Completed
        }
    }

    fun forget(title: DemoTitle) {
        requests.removeAll { it.title == title }
        issues.removeAll { it.title == title }
        mediaOverrides.remove(title.tmdbId)
    }

    /** What the server would report for [title]: blocked, set by hand, or read from its requests. */
    fun mediaStatus(title: DemoTitle): SeerrMediaStatusCode {
        val own = requests.filter { it.title == title }
        return when {
            blocklist.any { it.title == title } -> SeerrMediaStatusCode.Blocklisted
            mediaOverrides.containsKey(title.tmdbId) -> mediaOverrides.getValue(title.tmdbId)
            own.any { it.status == SeerrRequestStatusCode.Completed } -> SeerrMediaStatusCode.Available
            own.any { it.status == SeerrRequestStatusCode.Approved } -> SeerrMediaStatusCode.Processing
            own.any { it.status == SeerrRequestStatusCode.Pending } -> SeerrMediaStatusCode.Pending
            else -> SeerrMediaStatusCode.Unknown
        }
    }

    fun downloads(title: DemoTitle): List<SeerrDownloadStatusDto> =
        if (requests.any { it.title == title && it.download }) {
            listOf(
                SeerrDownloadStatusDto(
                    title = title.title,
                    size = DOWNLOAD_BYTES,
                    sizeLeft = DOWNLOAD_LEFT_BYTES,
                    timeLeft = DOWNLOAD_TIME_LEFT,
                    status = "downloading",
                ),
            )
        } else {
            emptyList()
        }

    private fun seed() {
        SEED_REQUESTS.forEach { seed ->
            val request = add(title(seed.title), seed.by, seed.status, seed.seasons, seed.hoursAgo)
            if (seed.status == SeerrRequestStatusCode.Approved) {
                request.modifiedBy = DEMO_USER_ID
                request.download = true
            }
            if (seed.status == SeerrRequestStatusCode.Declined) request.modifiedBy = DEMO_USER_ID
        }
        block(title("Parasite"), by = DEMO_USER_ID, agoHours = 48)
        val subtitles =
            addIssue(
                title("Metropolis"),
                SeerrIssueTypeCode.Subtitles,
                by = 2,
                "The subtitles drift about ten seconds late.",
                agoHours = 20,
            )
        comment(subtitles, DEMO_USER_ID, "Thanks, I will look at the file.", agoHours = 18)
        addIssue(title("Night of the Living Dead"), SeerrIssueTypeCode.Audio, by = 3, "The sound cuts out near the start.", agoHours = 5)
        val flicker =
            addIssue(title("Spirited Away"), SeerrIssueTypeCode.Video, by = 2, "There is a green flicker in one scene.", agoHours = 72)
        comment(flicker, DEMO_USER_ID, "Replaced the file. Please check.", agoHours = 48)
        flicker.status = SeerrIssueStatusCode.Resolved
    }

    private fun title(name: String): DemoTitle = DEMO_TITLES.first { it.title == name }
}

internal fun seedUsers() =
    listOf(
        SeerrUserDto(id = DEMO_USER_ID, displayName = "Alex", email = "alex@example.com", permissions = ADMIN, userType = LOCAL_USER),
        SeerrUserDto(id = 2, displayName = "Sam", email = "sam@example.com", permissions = 0, userType = LOCAL_USER),
        SeerrUserDto(id = 3, displayName = "Jordan", email = "jordan@example.com", permissions = 0, userType = LOCAL_USER),
    )

private class SeedRequest(
    val title: String,
    val by: Int,
    val hoursAgo: Long,
    val status: SeerrRequestStatusCode = SeerrRequestStatusCode.Pending,
    val seasons: List<Int> = emptyList(),
)

private val SEED_REQUESTS =
    listOf(
        SeedRequest("Sintel", by = 2, hoursAgo = 26),
        SeedRequest("Nosferatu", by = 3, hoursAgo = 3),
        SeedRequest("Severance", by = 3, hoursAgo = 7, seasons = listOf(1)),
        SeedRequest("Big Buck Bunny", by = 2, hoursAgo = 50, status = SeerrRequestStatusCode.Approved),
        SeedRequest("The Expanse", by = 3, hoursAgo = 30, status = SeerrRequestStatusCode.Approved, seasons = listOf(1, 2)),
        SeedRequest("Metropolis", by = 1, hoursAgo = 120, status = SeerrRequestStatusCode.Completed),
        SeedRequest("Night of the Living Dead", by = 2, hoursAgo = 144, status = SeerrRequestStatusCode.Completed),
        SeedRequest("Spirited Away", by = 1, hoursAgo = 216, status = SeerrRequestStatusCode.Completed),
        SeedRequest("Tears of Steel", by = 3, hoursAgo = 96, status = SeerrRequestStatusCode.Declined),
        SeedRequest("The Matrix", by = 2, hoursAgo = 60, status = SeerrRequestStatusCode.Failed),
    )
