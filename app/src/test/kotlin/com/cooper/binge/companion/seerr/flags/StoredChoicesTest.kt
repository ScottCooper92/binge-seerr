package com.cooper.binge.companion.seerr.flags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cooper.binge.companion.seerr.feedback.shakeToReportPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What a user chose in an earlier version is read back after the preference classes moved to the shared library. The key, the
 * file and the default are the ones that shipped (`seerr_discovery` and `seerr_feedback`), so a choice made before the switch holds
 * after it. Each test writes the old file with the raw key, then opens it the way the app now does.
 */
class StoredChoicesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private suspend fun written(
        file: File,
        key: String,
        value: Boolean?,
    ) {
        val scope = CoroutineScope(Job())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        if (value != null) store.edit { it[booleanPreferencesKey(key)] = value }
        // DataStore frees the file when its scope completes, so wait for that before the next one opens the same file.
        scope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun `a discovery opt-out stored by the shipped version is still off`() =
        runTest {
            val file = folder.newFile("seerr_discovery.preferences_pb")
            written(file, "discovery", false)

            val read = discoveryPreference(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

            assertFalse(read.value.first())
        }

    @Test
    fun `a stored discovery opt-in stays on`() =
        runTest {
            val file = folder.newFile("seerr_discovery_on.preferences_pb")
            written(file, "discovery", true)

            assertTrue(discoveryPreference(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }).value.first())
        }

    @Test
    fun `discovery is on for an install that never chose`() =
        runTest {
            val file = folder.newFile("seerr_discovery_none.preferences_pb")
            written(file, "discovery", null)

            assertTrue(discoveryPreference(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }).value.first())
        }

    @Test
    fun `a shake setting stored by the shipped version is still off`() =
        runTest {
            val file = folder.newFile("seerr_feedback.preferences_pb")
            written(file, "shake_to_report", false)

            assertFalse(shakeToReportPreference(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }).value.first())
        }

    @Test
    fun `shake to report is on until turned off`() =
        runTest {
            val file = folder.newFile("seerr_feedback_none.preferences_pb")
            val pref = shakeToReportPreference(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
            assertTrue(pref.value.first())

            pref.set(false)
            assertFalse(pref.value.first())
        }

    @Test
    fun `a choice written now is stored under the names that shipped`() =
        runTest {
            val file = folder.newFile("seerr_names.preferences_pb")
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

            discoveryPreference(store).set(false)
            shakeToReportPreference(store).set(false)

            val stored = store.data.first()
            assertEquals(false, stored[booleanPreferencesKey("discovery")])
            assertEquals(false, stored[booleanPreferencesKey("shake_to_report")])
        }
}
