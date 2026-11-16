package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

            assertEquals(emptyList<String>(), recents.ids.first())
        }

    /**
     * Opening a result remembers it and leaves the search at once, which clears the ViewModel the write was launched from. The write
     * has to land anyway: the screen is gone, the entry was opened.
     */
    @Test
    fun `an entry is remembered even when the screen that opened it is closed at once`() =
        runTest {
            val recents = recents()
            val screen = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))

            val write = screen.launch(start = CoroutineStart.UNDISPATCHED) { recents.remember("a") }
            screen.cancel()
            write.join()
            advanceUntilIdle()

            assertEquals(listOf("a"), recents.ids.first())
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
