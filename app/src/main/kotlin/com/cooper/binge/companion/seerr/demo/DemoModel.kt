package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrCountProbeDto
import com.cooper.binge.companion.seerr.seerr.SeerrIssueCountDto
import com.cooper.binge.companion.seerr.seerr.SeerrPageInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrQuotaDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestCountDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestsPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto

private const val DEMO_VERSION = "3.2.0"
private const val JELLYFIN = 2
private const val ADMIN = 2
private const val LOCAL_USER = 2

/** `user/{id}/quota`. */
private const val QUOTA_SEGMENTS = 3

/** The sample titles shown on the sign-in screen's backdrop: public-domain and Creative Commons films. */
private val BACKDROPS = listOf("/tCUpMX0CXQhWx6jrJ8sGB3ZwHw3.jpg")

/**
 * The demo server's state, in memory. A write changes what a later read answers, so approving a request in
 * the demo approves it, until the connection is forgotten: [reset] drops the lot, and the process ending does
 * the same. Nothing here is stored anywhere.
 */
internal class DemoModel {
    private var users = seedUsers()

    /** Back to the seed. Called when the demo connection is forgotten, so the next demo starts clean. */
    @Synchronized
    fun reset() {
        users = seedUsers()
    }

    /** The answer to [call]. The model is single-threaded by lock: a demo call is a map lookup, never slow. */
    @Synchronized
    fun answer(call: DemoCall): DemoAnswer =
        when (call.method to call.segments) {
            "GET" to listOf("status") -> ok(SeerrStatusDto(version = DEMO_VERSION))
            "GET" to listOf("settings", "public") -> ok(publicSettings())
            "GET" to listOf("backdrops") -> ok(BACKDROPS)
            "GET" to listOf("auth", "me") -> ok(users.first { it.id == DEMO_USER_ID })
            "POST" to listOf("auth", "logout") -> ok(emptyMap<String, String>())
            "GET" to listOf("request", "count") -> ok(SeerrRequestCountDto())
            "GET" to listOf("issue", "count") -> ok(SeerrIssueCountDto())
            "GET" to listOf("request") -> ok(SeerrRequestsPageDto())
            "GET" to listOf("user") -> ok(SeerrCountProbeDto(SeerrPageInfoDto(results = users.size, pages = 1, page = 1)))
            "GET" to listOf("blocklist") -> ok(SeerrCountProbeDto())
            else -> if (isQuota(call)) ok(SeerrQuotaDto()) else notInTheDemo
        }

    private fun isQuota(call: DemoCall): Boolean =
        call.method == "GET" && call.segments.size == QUOTA_SEGMENTS && call.segments[0] == "user" && call.segments[2] == "quota"

    private fun publicSettings() =
        SeerrPublicSettings(
            applicationTitle = "Seerr demo",
            mediaServerType = JELLYFIN,
            jellyfinServerName = "Demo library",
        )

    private fun seedUsers() =
        listOf(
            SeerrUserDto(id = DEMO_USER_ID, displayName = "Alex", email = "alex@example.com", permissions = ADMIN, userType = LOCAL_USER),
            SeerrUserDto(id = 2, displayName = "Sam", email = "sam@example.com", permissions = 0, userType = LOCAL_USER),
            SeerrUserDto(id = 3, displayName = "Jordan", email = "jordan@example.com", permissions = 0, userType = LOCAL_USER),
        )
}
