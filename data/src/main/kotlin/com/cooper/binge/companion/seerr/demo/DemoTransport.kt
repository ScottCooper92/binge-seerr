package com.cooper.binge.companion.seerr.demo

import okhttp3.Interceptor
import com.cooper.homelab.core.server.demo.DemoTransport as CoreDemoTransport

internal const val API_PREFIX = "/api/v1/"

/**
 * Answers every call to the demo server from [model], before the network: no socket is opened and nothing
 * leaves the device. A call to any other host passes through untouched, so this sits in every client and
 * is inert unless the saved server is the demo.
 *
 * It is the whole demo: the screens and the exported Service call the same [com.cooper.binge.companion.seerr.seerr.SeerrApi]
 * as against a real server, and read what this answers. The interceptor is homelab-core's; the routes are this app's.
 */
internal fun demoTransport(model: DemoModel): Interceptor =
    CoreDemoTransport(host = DEMO_HOST, pathPrefix = API_PREFIX, answer = model::answer)
