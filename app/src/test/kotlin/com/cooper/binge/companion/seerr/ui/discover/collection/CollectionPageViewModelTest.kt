package com.cooper.binge.companion.seerr.ui.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.collection.CollectionRepository
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

internal fun partFixture(
    id: Int,
    availability: TitleAvailability = TitleAvailability.None,
) = TitleResult(TitleKind.Movie, id, "Film $id", "${2000 + id}", null, null, "A story.", 7.5, availability, null)

internal fun collectionFixture(parts: List<TitleResult> = listOf(partFixture(1), partFixture(2))) =
    CollectionDetails(id = 2344, name = "The Collection", overview = "A saga.", backdropUrl = null, parts = parts)

private class FakeCollections(
    var collection: CollectionDetails = collectionFixture(),
    var failure: Throwable? = null,
) : CollectionRepository {
    val reads = mutableListOf<Int>()

    override suspend fun collection(id: Int): CollectionDetails {
        reads += id
        return failure?.let { throw it } ?: collection
    }
}

class CollectionPageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the page reads its collection once`() {
        val repository = FakeCollections()
        val vm = CollectionPageViewModel(repository, 2344)

        assertEquals(listOf(2344), repository.reads)
        assertEquals("The Collection", (vm.uiState.value as CollectionPageUiState.Ready).collection.name)
    }

    @Test
    fun `a failed read is an error that retries`() {
        val repository = FakeCollections(failure = IOException("down"))
        val vm = CollectionPageViewModel(repository, 2344)
        assertTrue(vm.uiState.value is CollectionPageUiState.Error)

        repository.failure = null
        vm.load()

        assertTrue(vm.uiState.value is CollectionPageUiState.Ready)
    }

    @Test
    fun `only the films nothing was asked for count as left to request`() {
        val collection =
            collectionFixture(
                listOf(
                    partFixture(1, TitleAvailability.Available),
                    partFixture(2, TitleAvailability.Pending),
                    partFixture(3),
                    partFixture(4, TitleAvailability.Blocklisted),
                ),
            )

        assertEquals(1, collection.toRequest)
    }
}
