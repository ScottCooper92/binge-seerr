package io.github.scottcooper92.binge.seerr.util

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import java.util.concurrent.ConcurrentLinkedQueue

private const val STATUS_PATH = "/api/v1/status"
private const val SETTINGS_PATH = "/api/v1/settings/public"

/**
 * The profile read issues `status` and `settings/public` concurrently, so which arrives first is
 * not the script's to decide. A queue answers by arrival order and would hand one call the other's
 * body; these two answer by path instead and leave every other request to the ordinary queue.
 */
class ProfileRoutes {
    private val statuses = ConcurrentLinkedQueue<MockResponse>()
    private val settings = ConcurrentLinkedQueue<MockResponse>()

    fun add(
        status: MockResponse,
        settings: MockResponse,
    ) {
        statuses += status
        this.settings += settings
    }

    fun answer(path: String): MockResponse? =
        when {
            path.endsWith(STATUS_PATH) -> statuses.poll()
            path.endsWith(SETTINGS_PATH) -> settings.poll()
            else -> null
        }
}

class ProfileRoutingDispatcher : QueueDispatcher() {
    val routes = ProfileRoutes()

    override fun dispatch(request: RecordedRequest): MockResponse = routes.answer(request.url.encodedPath) ?: super.dispatch(request)
}

/** Routes profile answers by path. Set before any [MockWebServer.enqueue]: swapping dispatchers would drop what was queued. */
fun MockWebServer.routeProfiles(): MockWebServer = apply { dispatcher = ProfileRoutingDispatcher() }

/** Queues one profile read's two answers, each served to its own path whichever call arrives first. */
fun MockWebServer.enqueueProfile(
    status: MockResponse,
    settings: MockResponse,
) {
    val routing = dispatcher as? ProfileRoutingDispatcher ?: error("call routeProfiles() on the server first")
    routing.routes.add(status, settings)
}
