package com.cooper.binge.companion.seerr.flags

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The Remote Config key of [DiscoveryFlag]. */
const val DISCOVERY_FLAG_KEY = "discovery_enabled"

private const val MIN_FETCH_SECONDS = 3_600L

/**
 * Whether the app shows the discovery surfaces (#2039). Off until Remote Config turns it on, and off in a build with no
 * Firebase app. With it off the app is exactly as it was before the epic.
 *
 * It is this app's own flag because the shared library's flag registry is a closed enum an app cannot add to. It moves
 * there if that is ever opened up.
 */
interface DiscoveryFlag {
    val enabled: StateFlow<Boolean>

    /** Fetches fresh remote values; a failed fetch keeps the last ones. */
    suspend fun refresh()
}

/** Where the remote value comes from. */
interface DiscoveryFlagSource {
    /** The last value this device activated; off where there is none. May read from disk. */
    fun current(): Boolean

    /** Fetches and activates, then returns the new value; null when nothing could be fetched. */
    suspend fun fetch(): Boolean?
}

/** A local answer that wins over the remote one, for a developer trying the flag on a device. */
interface DiscoveryFlagOverride {
    /** Null follows the remote value. */
    val value: Flow<Boolean?>

    /** Null clears the override. */
    suspend fun set(value: Boolean?)
}

/** What a release build has: no override, and nowhere to put one. */
object NoDiscoveryFlagOverride : DiscoveryFlagOverride {
    override val value: Flow<Boolean?> = emptyFlow()

    override suspend fun set(value: Boolean?) = Unit
}

/** The override kept in the same DataStore as the shared flags' overrides, under the same `override_` prefix. */
class DataStoreDiscoveryFlagOverride(
    private val dataStore: DataStore<Preferences>,
) : DiscoveryFlagOverride {
    private val key = booleanPreferencesKey("override_$DISCOVERY_FLAG_KEY")

    override val value: Flow<Boolean?> = dataStore.data.map { it[key] }.distinctUntilChanged()

    override suspend fun set(value: Boolean?) {
        dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}

/**
 * [DiscoveryFlag] over a [DiscoveryFlagSource], with [override] on top. [initialOverride] is what the override held at
 * start, so a read right after a cold start does not lose a race with the store; the source's cache is read once, here.
 */
class RemoteDiscoveryFlag(
    private val source: DiscoveryFlagSource,
    override: DiscoveryFlagOverride,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    initialOverride: Boolean? = null,
) : DiscoveryFlag {
    private val remote = MutableStateFlow(source.current())

    override val enabled: StateFlow<Boolean> =
        combine(remote, override.value.onStart { emit(initialOverride) }) { remote, local -> local ?: remote }
            .stateIn(scope, SharingStarted.Eagerly, initialOverride ?: remote.value)

    override suspend fun refresh() {
        withContext(io) {
            remote.value = source.current()
            source.fetch()?.let { remote.value = it }
        }
    }
}

/**
 * Firebase Remote Config. A build without the app's `google-services.json` has no Firebase app, and the flag stays off.
 * It shares the Remote Config instance with the shared flags, so it sets no defaults (an absent key reads as off) and
 * keeps their fetch interval.
 */
class FirebaseDiscoveryFlagSource(
    private val context: Context,
) : DiscoveryFlagSource {
    private val config: FirebaseRemoteConfig? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) return@lazy null
        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(MIN_FETCH_SECONDS).build())
        }
    }

    override fun current(): Boolean = config?.getBoolean(DISCOVERY_FLAG_KEY) ?: false

    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetch(): Boolean? {
        val remote = config ?: return null
        val fetched =
            suspendCancellableCoroutine { continuation ->
                try {
                    remote.fetchAndActivate().addOnCompleteListener { continuation.resume(it.isSuccessful) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continuation.resume(false)
                }
            }
        return if (fetched) remote.getBoolean(DISCOVERY_FLAG_KEY) else null
    }
}
