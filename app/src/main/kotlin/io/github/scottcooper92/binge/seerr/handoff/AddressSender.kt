package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.logWarning
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Sends a server address to a television's hand-off listener. */
fun interface AddressSender {
    /** Whether the TV accepted [address]. */
    suspend fun send(
        target: TvHandOffTarget,
        address: String,
    ): Boolean
}

/**
 * Posts the address as the page's own form would, to the one URL the TV answers on.
 *
 * Its own client, not the Seerr one: nothing of the Seerr session — no cookie, no API key, no
 * interceptor — can reach a request it never passes through. That also keeps it out of
 * `CleartextGuard`'s way, which guards only the Seerr client; this request is plain HTTP to a
 * private address, which that guard would allow anyway and the network security config permits.
 * No redirects: the TV never sends one, and following one could carry the address off the LAN.
 */
internal class OkHttpAddressSender(
    private val client: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Why a send failed, for the device log; a no-op where there is no Android log (unit tests). */
    private val warn: (String) -> Unit = {},
) : AddressSender {
    @Inject
    constructor() : this(
        OkHttpClient
            .Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            // Never replay the POST. The TV takes exactly one address and then stops listening, so a silent
            // retry after a slow or dropped first response reaches a closed port and reports a failure for a
            // send that worked.
            .retryOnConnectionFailure(false)
            .build(),
        warn = logWarning(TAG),
    )

    override suspend fun send(
        target: TvHandOffTarget,
        address: String,
    ): Boolean =
        withContext(dispatcher) {
            val request =
                Request
                    .Builder()
                    .url(target.url)
                    .post(FormBody.Builder().add("address", address).build())
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code != HTTP_OK) warn("The TV answered ${response.code}")
                    response.code == HTTP_OK
                }
            } catch (e: IOException) {
                warn("Sending the address failed: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }

    private companion object {
        const val TAG = "AddressSender"

        // Long enough for a phone whose LAN traffic goes through a VPN to hear back.
        const val TIMEOUT_SECONDS = 15L
        const val HTTP_OK = 200
    }
}
