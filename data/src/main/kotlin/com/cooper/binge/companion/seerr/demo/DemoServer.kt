package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import okhttp3.HttpUrl

/**
 * The demo server's address. `.invalid` is reserved (RFC 2606) and never resolves, so a request that
 * somehow escaped the demo transport would fail rather than reach a stranger's host.
 */
internal const val DEMO_HOST = "demo.seerr.invalid"

/** The base URL the demo connection is saved under, in the same shape as any other server's. */
const val DEMO_ORIGIN = "https://$DEMO_HOST/"

/** The demo's session cookie value and the id of the signed-in demo user. Neither is a secret. */
internal const val DEMO_COOKIE = "demo"
internal const val DEMO_USER_ID = 1

/** What the handshake names as the provider on the demo, so a host that shows the name shows that this is sample data. */
const val DEMO_PROVIDER_NAME = "Seerr (demo)"

/** The credentials of the demo connection: a session as the demo administrator. */
fun demoCredentials(): SeerrCredentials = SeerrCredentials(DEMO_ORIGIN, SeerrAuth.Session(DEMO_COOKIE, DEMO_USER_ID))

/** Whether [this] is the demo server's address. */
fun String.isDemoOrigin(): Boolean = equals(DEMO_ORIGIN, ignoreCase = true)

/** Whether [this] is the saved connection to the demo server. */
val SeerrCredentials.isDemo: Boolean get() = baseUrl.isDemoOrigin()

internal fun HttpUrl.isDemo(): Boolean = host == DEMO_HOST
