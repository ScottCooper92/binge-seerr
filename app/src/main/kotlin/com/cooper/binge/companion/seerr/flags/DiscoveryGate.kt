package com.cooper.binge.companion.seerr.flags

import com.cooper.homelab.core.flags.FeatureFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the app shows discovery: the remote flag AND the user's own preference. It is the one place the two are combined, so
 * nothing else reads `flags[DiscoveryEnabled]` directly. A debug override forces the remote flag; the preference sits on top.
 */
interface DiscoveryGate {
    /** The combined value. */
    val enabled: StateFlow<Boolean>

    /** False until the user's preference has been read, so a first value is not mistaken for a change. */
    val ready: StateFlow<Boolean>

    /** The remote flag alone: whether the Discovery row is shown at all. */
    val offered: Flow<Boolean>

    /** The user's own choice, whatever the flag says. */
    val preference: Flow<Boolean>

    suspend fun setPreference(enabled: Boolean)
}

/** A gate that is off and offers nothing, for a screen built without one. */
object NoDiscoveryGate : DiscoveryGate {
    override val enabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    override val offered: Flow<Boolean> = flowOf(false)
    override val preference: Flow<Boolean> = flowOf(true)

    override suspend fun setPreference(enabled: Boolean) = Unit
}

class PrefsDiscoveryGate(
    featureFlags: FeatureFlags,
    private val prefs: DiscoveryPrefs,
    scope: CoroutineScope,
) : DiscoveryGate {
    override val offered: Flow<Boolean> = featureFlags.flags.map { it[DiscoveryEnabled] }.distinctUntilChanged()
    override val preference: Flow<Boolean> = prefs.enabled

    override val ready: StateFlow<Boolean> = preference.map { true }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Until the preference has been read the flag alone decides, which is right for everyone who has not opted out. */
    override val enabled: StateFlow<Boolean> =
        combine(offered, preference) { flag, pref -> flag && pref }
            .stateIn(scope, SharingStarted.Eagerly, featureFlags.flags.value[DiscoveryEnabled])

    override suspend fun setPreference(enabled: Boolean) = prefs.setEnabled(enabled)
}
