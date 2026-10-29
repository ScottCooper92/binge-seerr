package com.cooper.binge.companion.seerr.flags

import android.content.Context
import com.binge.companion.sdk.attempt
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/** Where flag values come from, as far as [RemoteFeatureFlags] is concerned. */
interface RemoteFlagSource {
    /**
     * The last values this device activated, every flag false where it has none. Reads Remote Config's cache from
     * disk, so the first call may wait on it.
     */
    fun current(): Flags

    /** Fetches and activates, then returns the new values; null when nothing could be fetched. */
    suspend fun fetch(): Flags?
}

/** A source with nothing behind it, so every flag stays at its default of off. */
object NoRemoteFlagSource : RemoteFlagSource {
    override fun current(): Flags = Flags()

    override suspend fun fetch(): Flags? = null
}

/**
 * Firebase Remote Config. A build without this app's `google-services.json` has no Firebase app, and there
 * every flag stays off rather than failing. Remote Config keeps the last activated values on disk, so a cold
 * start reads them before the first fetch lands.
 */
class FirebaseRemoteFlagSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RemoteFlagSource {
        private val config: FirebaseRemoteConfig? by lazy {
            if (FirebaseApp.getApps(context).isEmpty()) return@lazy null
            FirebaseRemoteConfig.getInstance().apply {
                setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = MIN_FETCH_SECONDS })
                setDefaultsAsync(Flag.entries.associate { it.key to false })
            }
        }

        override fun current(): Flags = config?.let(::read) ?: Flags()

        override suspend fun fetch(): Flags? {
            val remote = config ?: return null
            val fetched =
                suspendCancellableCoroutine { continuation ->
                    attempt { remote.fetchAndActivate() }
                        .onSuccess { task -> task.addOnCompleteListener { continuation.resume(it.isSuccessful) } }
                        .onFailure { continuation.resume(false) }
                }
            return if (fetched) read(remote) else null
        }

        private fun read(remote: FirebaseRemoteConfig) =
            Flags(
                bingePromotion = remote.getBoolean(Flag.BingePromotion.key),
            )

        private companion object {
            const val MIN_FETCH_SECONDS = 3_600L
        }
    }
