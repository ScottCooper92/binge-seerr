package com.cooper.binge.companion.seerr.flags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.homelab.core.flags.BooleanPreference

/** The name this choice has been stored under since it shipped. A different one would lose what users chose. */
internal const val DISCOVERY_KEY = "discovery"

/**
 * Whether the user wants the discovery surfaces: on until turned off. It only counts while the remote flag is on, which core's
 * `PreferenceGate` combines.
 */
fun discoveryPreference(dataStore: DataStore<Preferences>): BooleanPreference = BooleanPreference(dataStore, DISCOVERY_KEY, default = true)
