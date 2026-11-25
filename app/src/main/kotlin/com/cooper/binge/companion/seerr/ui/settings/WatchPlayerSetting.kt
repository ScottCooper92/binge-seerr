package com.cooper.binge.companion.seerr.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.binge.companion.seerr.flags.WatchPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Where the user wants Watch to play a title: read by the title page, changed under This app. */
interface WatchPlayerSetting {
    val value: Flow<WatchPlayer>

    suspend fun set(player: WatchPlayer)
}

/** A setting nothing changes: [WatchPlayer.Automatic], for screens that have no store. */
class FixedWatchPlayerSetting(
    player: WatchPlayer = WatchPlayer.Automatic,
) : WatchPlayerSetting {
    private val current = MutableStateFlow(player)
    override val value: Flow<WatchPlayer> = current

    override suspend fun set(player: WatchPlayer) {
        current.value = player
    }
}

/** The name this choice is stored under. A different one would lose what users chose. */
internal const val WATCH_PLAYER_KEY = "watch_player"

/** The choice on the device. Nothing is written until the user chooses, so an install that never did reads [WatchPlayer.Automatic]. */
class DataStoreWatchPlayerSetting(
    private val dataStore: DataStore<Preferences>,
) : WatchPlayerSetting {
    private val key = stringPreferencesKey(WATCH_PLAYER_KEY)

    override val value: Flow<WatchPlayer> = dataStore.data.map { WatchPlayer.fromKey(it[key]) }.distinctUntilChanged()

    override suspend fun set(player: WatchPlayer) {
        dataStore.edit { it[key] = player.key }
    }
}
