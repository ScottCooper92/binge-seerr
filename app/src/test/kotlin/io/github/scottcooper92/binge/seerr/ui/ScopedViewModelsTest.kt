package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The TV shell's overlays each open in a [ScopedViewModels] of their own, keyed by what they show (#789). This holds
 * what that gives them: a view model made for the id on screen, never one left from another, and cleared when the
 * overlay closes or moves on, with no `key` on the lookup to forget (#790).
 */
@RunWith(RobolectricTestRunner::class)
class ScopedViewModelsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val made = mutableListOf<Probe>()
    private var openId by mutableStateOf<Int?>(1)

    @Test
    fun `each id gets a view model made for it, and the last one is cleared when it moves on or closes`() {
        rule.setContent {
            openId?.let { id ->
                ScopedViewModels("detail-$id") {
                    viewModel<Probe>(factory = viewModelFactory { initializer { Probe(id).also { made += it } } })
                }
            }
        }
        rule.waitForIdle()
        assertEquals(listOf(1), made.map { it.id })

        openId = 2
        rule.waitForIdle()
        assertEquals(listOf(1, 2), made.map { it.id })
        assertEquals(listOf(true, false), made.map { it.cleared })

        openId = null
        rule.waitForIdle()
        assertEquals(listOf(true, true), made.map { it.cleared })

        openId = 1
        rule.waitForIdle()
        assertEquals("Reopening makes a new one rather than finding the last", 3, made.size)
        assertEquals(1, made.last().id)
    }

    private class Probe(
        val id: Int,
    ) : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }
}
