package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The entries last opened from the settings search: newest first, each once, only the last few, and clearable. */
class RecentSearchesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.recents() =
        RecentSearches(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                folder.newFile("r.preferences_pb")
            },
        )

    @Test
    fun `nothing is remembered to begin with`() =
        runTest {
            assertEquals(emptyList<String>(), recents().ids.first())
        }

    @Test
    fun `the newest leads, and one opened again moves up instead of appearing twice`() =
        runTest {
            val recents = recents()
            recents.remember("a")
            recents.remember("b")
            recents.remember("c")
            recents.remember("a")

            assertEquals(listOf("a", "c", "b"), recents.ids.first())
        }

    @Test
    fun `only the last few are kept`() =
        runTest {
            val recents = recents()
            (1..MAX_RECENT + 3).forEach { recents.remember("entry$it") }

            val kept = recents.ids.first()

            assertEquals(MAX_RECENT, kept.size)
            assertEquals("entry${MAX_RECENT + 3}", kept.first())
        }

    @Test
    fun `a blank id is not remembered`() =
        runTest {
            val recents = recents()
            recents.remember("")
            recents.remember("  ")

            assertEquals(emptyList<String>(), recents.ids.first().filter { it.isEmpty() })
        }

    @Test
    fun `clear forgets everything`() =
        runTest {
            val recents = recents()
            recents.remember("a")
            recents.clear()

            assertEquals(emptyList<String>(), recents.ids.first())
        }
}
