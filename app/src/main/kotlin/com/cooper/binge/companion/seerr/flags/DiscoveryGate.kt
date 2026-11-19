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
    /**
     * The combined value, from one emission: null until both the flag and the user's preference have been read, then never null.
     * A reader that must tell a toggle from a first value keys on the null, so the two cannot disagree.
     */
    val resolved: StateFlow<Boolean?>

    /** The remote flag alone, for the moments before [resolved] has a value. Right for everyone who has not opted out. */
    val fallback: Boolean

    /** The remote flag alone: whether the Discovery row is shown at all. */
    val offered: Flow<Boolean>

    /** The user's own choice, whatever the flag says. */
    val preference: Flow<Boolean>

    suspend fun setPreference(enabled: Boolean)

    /** What to show now: the resolved value, or the [fallback] until there is one. */
    fun current(): Boolean = resolved.value ?: fallback
}

/** A gate that is off and offers nothing, for a screen built without one. */
object NoDiscoveryGate : DiscoveryGate {
    override val resolved: StateFlow<Boolean?> = MutableStateFlow(false)
    override val fallback: Boolean = false
    override val offered: Flow<Boolean> = flowOf(false)
    override val preference: Flow<Boolean> = flowOf(true)

    override suspend fun setPreference(enabled: Boolean) = Unit
}

class PrefsDiscoveryGate(
    private val featureFlags: FeatureFlags,
    private val prefs: DiscoveryPrefs,
    scope: CoroutineScope,
) : DiscoveryGate {
    override val offered: Flow<Boolean> = featureFlags.flags.map { it[DiscoveryEnabled] }.distinctUntilChanged()
    override val preference: Flow<Boolean> = prefs.enabled
    override val fallback: Boolean get() = featureFlags.flags.value[DiscoveryEnabled]

    override val resolved: StateFlow<Boolean?> =
        combine<Boolean, Boolean, Boolean?>(offered, preference) { flag, pref -> flag && pref }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun setPreference(enabled: Boolean) = prefs.setEnabled(enabled)
}
