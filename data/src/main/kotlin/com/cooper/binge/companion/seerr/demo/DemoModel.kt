package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrStatusDto
import com.cooper.homelab.core.server.demo.DemoAnswer
import com.cooper.homelab.core.server.demo.DemoCall
import com.cooper.homelab.core.server.demo.DemoRoute
import com.cooper.homelab.core.server.demo.answer
import com.cooper.homelab.core.server.demo.route
import java.time.Instant

internal const val DEMO_VERSION = "3.2.0"
private const val JELLYFIN = 2

/** The artwork behind a sign-in page and behind each demo title's hero: stills of the open films in [DEMO_TITLES]. */
internal val DEMO_BACKDROPS =
    listOf(
        "/xtdybjRRZ15mCrPOvEld305myys.jpg",
        "/msqeiEyIRpPAtrCeRGFNZQ9tkJL.jpg",
        "/fOy6SL5Zs2PFcNXwqEPIDPrLB1q.jpg",
        "/5KtmBSqFtHY3I9t8lgH27Mc0bqY.jpg",
        "/cA9iGtvjRGJHzDBfrq48l0eyCvA.jpg",
        "/eeMoFKxjjiCi6iep2GEZtSAMYIr.jpg",
    )

/**
 * The demo server's state, in memory. A write changes what a later read answers, so approving a request in
 * the demo approves it, until the connection is forgotten: [reset] drops the lot, and the process ending does
 * the same. Nothing here is stored anywhere.
 */
internal class DemoModel(
    private val clock: () -> Instant = Instant::now,
) {
    private var data = DemoData(clock)
    private var routes = routesFor(data)

    /** Back to the seed. Called when the demo connection is forgotten, so the next demo starts clean. */
    @Synchronized
    fun reset() {
        data = DemoData(clock)
        routes = routesFor(data)
    }

    /** Whether the demo is viewed as a user who may only request, rather than the administrator it seeds. */
    val viewer: Boolean
        @Synchronized get() = data.viewer

    /** Views the demo as a requester or as the administrator; `auth/me` answers the matching permissions from the next read. */
    @Synchronized
    fun viewAs(viewer: Boolean) = data.viewAs(viewer)

    /** The answer to [call]. A demo call is a list lookup, never slow, so one lock serves them all. */
    @Synchronized
    fun answer(call: DemoCall): DemoAnswer = routes.answer(call) ?: notInTheDemo

    private fun routesFor(data: DemoData): List<DemoRoute> =
        publicRoutes(data) +
            data.requestRoutes() +
            data.mediaRoutes() +
            data.titleRoutes() +
            data.seriesRoutes() +
            data.personRoutes() +
            data.discoverRoutes() +
            data.issueRoutes() +
            data.blocklistRoutes() +
            data.watchlistRoutes() +
            data.userRoutes() +
            data.settingsRoutes() +
            data.maintenanceRoutes() +
            lookupRoutes()

    private fun publicRoutes(data: DemoData): List<DemoRoute> =
        listOf(
            route("GET", "status") { _, _ -> ok(SeerrStatusDto(version = DEMO_VERSION)) },
            route("GET", "settings", "public") { _, _ -> ok(publicSettings()) },
            route("GET", "backdrops") { _, _ -> ok(DEMO_BACKDROPS) },
            route("GET", "auth", "me") { _, _ -> ok(data.users.first { it.id == DEMO_USER_ID }) },
            route("POST", "auth", "logout") { _, _ -> done },
        )

    private fun publicSettings() =
        SeerrPublicSettings(
            initialized = true,
            applicationTitle = "Seerr demo",
            mediaServerType = JELLYFIN,
            jellyfinServerName = "Demo library",
        )
}
