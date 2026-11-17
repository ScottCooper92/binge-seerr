package com.cooper.binge.companion.seerr.seerr

import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.isLocalOrPrivateHost
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * The host, when [this] explicitly asks for cleartext to a PUBLIC one: an `http://` scheme whose host
 * is not loopback, private, LAN or tailnet. The secret would cross the network unencrypted, so setup
 * refuses it unless the user opts in for that host (see `CleartextConsent`). Null for anything else.
 */
fun String.insecurePublicHostOrNull(): String? = trim().toHttpUrlOrNull()?.takeIf { it.isCleartextToPublicHost() }?.host

/** Plain `http` to a host that is not loopback, private, LAN or tailnet: what needs the user's opt-in. */
internal fun HttpUrl.isCleartextToPublicHost(): Boolean = !isHttps && !isLocalOrPrivateHost(host)

/** A request this app refused to send: plain HTTP to a public host the user has not opted in for. */
class CleartextRefusedException(
    val host: String,
) : IOException("Refused plain HTTP to $host: it is a public host and the user has not opted in for it")

/**
 * Refuses plain HTTP to a public host that [consent] does not cover.
 *
 * A **network** interceptor, so it judges every hop, including a redirect from an `https` server
 * down to `http`, and runs before a byte of the request is written. Setup already refuses such an
 * address without the opt-in; this is the backstop for every other way a URL reaches a client.
 */
internal class CleartextGuard(
    private val consent: CleartextConsent,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        // runBlocking on an OkHttp thread, and only for the one case that needs the answer.
        if (url.isCleartextToPublicHost() && !runBlocking { consent.allows(url.host) }) throw CleartextRefusedException(url.host)
        return chain.proceed(chain.request())
    }
}
