package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.homelab.core.foundation.ServerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The recents of the one server in the shared per-server store, over the list a released version kept under the bare
 * `recent_entries` key: it is moved to the server's key, so a clear stays cleared across launches.
 */
class ServerRecentSearchesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val legacy = stringPreferencesKey("recent_entries")
    private val keyed = stringPreferencesKey("recent_entries.server-1")

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("r.preferences_pb") }

    private fun recents(data: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) =
        ServerRecentSearches(data) { ServerId("server-1") }

    @Test
    fun `nothing is remembered to begin with`() =
        runTest {
            assertEquals(emptyList<String>(), recents(dataStore()).ids.first())
        }

    @Test
    fun `the list a released version wrote is offered, newest first, and kept under the server's key`() =
        runTest {
            val data = dataStore()
            data.edit { it[legacy] = "server.Logs|\naccount.Password|\nthis-app|" }

            val recents = recents(data)

            assertEquals(listOf("server.Logs|", "account.Password|", "this-app|"), recents.ids.first())
            assertEquals("server.Logs|\naccount.Password|\nthis-app|", data.data.first()[keyed])
            assertNull(data.data.first()[legacy])
        }

    @Test
    fun `what is remembered goes to the server's key and the old one is gone`() =
        runTest {
            val data = dataStore()
            data.edit { it[legacy] = "this-app|" }
            val recents = recents(data)

            recents.remember("licences|")

            assertEquals(listOf("licences|", "this-app|"), recents.ids.first())
            assertNull(data.data.first()[legacy])
        }

    @Test
    fun `the old list is read once, so clearing does not bring it back`() =
        runTest {
            val data = dataStore()
            data.edit { it[legacy] = "this-app|" }
            val recents = recents(data)
            recents.ids.first()

            recents.clear()

            assertEquals(emptyList<String>(), recents.ids.first())
            assertNull(data.data.first()[keyed])
        }

    @Test
    fun `a clear stays cleared when the app is reopened`() =
        runTest {
            val data = dataStore()
            data.edit { it[legacy] = "this-app|" }
            val first = recents(data)
            first.ids.first()
            first.clear()

            assertEquals(emptyList<String>(), recents(data).ids.first())
        }

    @Test
    fun `a clear before the first read, as a server change does, is not undone on the next launch`() =
        runTest {
            val data = dataStore()
            data.edit { it[legacy] = "this-app|" }
            recents(data).clear()

            assertEquals(emptyList<String>(), recents(data).ids.first())
        }
}
