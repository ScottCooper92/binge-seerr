package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.logWarning
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * The phone's side of signing a television in after it has the address: what the TV is doing, and a way to hand it
 * credentials. Only a code scanned from the screen carries the key this needs, so a target without one has nothing
 * to offer here.
 */
interface TvSignInClient {
    /** Where the TV is, or null when it cannot be reached or answers with anything but its status. */
    suspend fun status(target: TvHandOffTarget): HandOffStatus?

    /**
     * Seals [credentials] for [target] and posts them; the number of the attempt the TV counted them as, or null if it
     * did not take them. A TV that took them may still refuse the sign-in, which its status says once
     * [HandOffStatus.attempt] has reached that number.
     */
    suspend fun send(
        target: TvHandOffTarget,
        credentials: HandOffCredentials,
    ): Int?
}

/**
 * Talks to the listener the way [OkHttpAddressSender] does, with its own client for the same reasons, and one more
 * rule: the credentials are sealed with the key from the code before they reach the request, so what crosses the
 * LAN is ciphertext the TV alone can open. If the target has no key, nothing is sent.
 */
internal class OkHttpTvSignInClient(
    private val client: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val warn: (String) -> Unit = {},
) : TvSignInClient {
    @Inject
    constructor() : this(handOffHttpClient(), warn = logWarning(TAG))

    override suspend fun status(target: TvHandOffTarget): HandOffStatus? =
        withContext(dispatcher) {
            val request =
                Request
                    .Builder()
                    .url(target.statusUrl)
                    .get()
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code != HTTP_OK) {
                        warn("The TV answered ${response.code} for its status")
                        null
                    } else {
                        runCatching { JSON.decodeFromString<HandOffStatus>(response.body.string()) }.getOrNull()
                    }
                }
            } catch (e: IOException) {
                warn("Reading the TV's status failed: ${e.javaClass.simpleName}")
                null
            }
        }

    override suspend fun send(
        target: TvHandOffTarget,
        credentials: HandOffCredentials,
    ): Int? {
        val key = target.key ?: return null
        val sealed = key.seal(JSON.encodeToString(credentials).toByteArray(Charsets.UTF_8), context = target.token)
        return withContext(dispatcher) {
            val request =
                Request
                    .Builder()
                    .url(target.credentialsUrl)
                    .post(FormBody.Builder().add("sealed", sealed).build())
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    // The status code only: the body says nothing a failure's reason would help with, and logging it is never worth the risk.
                    if (response.code != HTTP_OK) {
                        warn("The TV answered ${response.code} to the credentials")
                        null
                    } else {
                        runCatching { JSON.decodeFromString<HandOffTaken>(response.body.string()).attempt }.getOrNull()
                    }
                }
            } catch (e: IOException) {
                warn("Sending the credentials failed: ${e.javaClass.simpleName}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "TvSignInClient"
        const val HTTP_OK = 200
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
