package com.cooper.binge.companion.seerr.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val AVAILABLE_NOTIFICATIONS = booleanPreferencesKey("available_notifications")

/**
 * What the user has chosen about how the host app treats this one. Today that is one switch: whether the
 * host announces a requested title when it becomes available. It is off until turned on, because the user
 * may already hear it from Seerr itself, and the host reads it from `ListRequestsResponse`, so a change
 * reaches it on its next call and needs no handshake.
 */
class HostPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val availableNotifications: Flow<Boolean> = dataStore.data.map { it[AVAILABLE_NOTIFICATIONS] ?: false }.distinctUntilChanged()

    suspend fun isAvailableNotificationsEnabled(): Boolean = availableNotifications.first()

    suspend fun setAvailableNotifications(enabled: Boolean) {
        dataStore.edit { it[AVAILABLE_NOTIFICATIONS] = enabled }
    }
}
