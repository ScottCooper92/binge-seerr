package com.cooper.binge.companion.seerr.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.binge.companion.seerr.flags.WatchPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchPlayerSettingTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `an install that never chose reads automatic and writes nothing`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("w.preferences_pb") }
            assertEquals(WatchPlayer.Automatic, DataStoreWatchPlayerSetting(store).value.first())
            assertTrue(
                store.data
                    .first()
                    .asMap()
                    .isEmpty(),
            )
        }

    @Test
    fun `a choice is kept and read back`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("w.preferences_pb") }
            val setting = DataStoreWatchPlayerSetting(store)
            WatchPlayer.entries.forEach {
                setting.set(it)
                assertEquals(it, setting.value.first())
            }
        }

    @Test
    fun `a name this build does not know reads automatic`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("w.preferences_pb") }
            store.edit { it[stringPreferencesKey(WATCH_PLAYER_KEY)] = "a_player_from_a_later_build" }
            assertEquals(WatchPlayer.Automatic, DataStoreWatchPlayerSetting(store).value.first())
        }

    @Test
    fun `the stored names are the ones that shipped`() {
        assertEquals(
            listOf("automatic", "ask", "ephyra", "binge", "media_server"),
            WatchPlayer.entries.map { it.key },
        )
    }
}
