package com.cooper.binge.companion.seerr.ui.discover.collection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.template.CollectionDetailState
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionDetailMappingTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun content(state: CollectionPageUiState) = (state.toDetailState(resources) as CollectionDetailState.Content).collection

    @Test
    fun `loading and failure map to the template's states with the failure's kind and a retry`() {
        assertTrue(CollectionPageUiState.Loading.toDetailState(resources) is CollectionDetailState.Loading)

        val failed = CollectionPageUiState.Error(SeerrError.Unreachable).toDetailState(resources) as CollectionDetailState.Failed
        assertEquals(ErrorKind.Network, failed.kind)
        assertTrue(failed.retryable)
    }

    @Test
    fun `a collection keeps its films in order with the count and no poster beside the title`() {
        val detail = content(CollectionPageUiState.Ready(collectionFixture()))

        assertEquals("The Collection", detail.title)
        assertEquals("2 films", detail.metaText)
        assertNull(detail.posterUrl)
        assertEquals(listOf("1", "2"), detail.parts.map { it.key })
    }

    @Test
    fun `the count says how many are left to request only for a viewer who may request`() {
        assertEquals("2 films · 2 to request", content(CollectionPageUiState.Ready(collectionFixture(), mayRequest = true)).metaText)
    }

    @Test
    fun `a film carries its rating its year and the start of its synopsis`() {
        val film = TitleResult(TitleKind.Movie, 603, "The Matrix", "1999", "/p.jpg", null, "A hacker.", 8.2, TitleAvailability.None, null)
        val result = film.toResult()

        assertEquals("603", result.key)
        assertEquals(8.2f, result.rating)
        assertEquals("1999", result.meta)
        assertEquals("A hacker.", result.blurb)
        assertNull(film.copy(rating = 0.0, overview = " ").toResult().rating)
        assertNull(film.copy(overview = " ").toResult().blurb)
    }
}
