package com.cooper.binge.companion.seerr.ui.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.collection.CollectionRepository
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import com.cooper.binge.companion.seerr.util.RecordingCrashBreadcrumbs
import com.cooper.binge.companion.seerr.util.awaitEvent
import com.cooper.homelab.core.telemetry.NoOpCrashBreadcrumbs
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

internal fun partFixture(
    id: Int,
    availability: TitleAvailability = TitleAvailability.None,
) = TitleResult(TitleKind.Movie, id, "Film $id", "${2000 + id}", null, null, "A story.", 7.5, availability, null)

internal fun collectionFixture(parts: List<TitleResult> = listOf(partFixture(1), partFixture(2))) =
    CollectionDetails(id = 2344, name = "The Collection", overview = "A saga.", backdropUrl = null, posterUrl = null, parts = parts)

private class FakeCollections(
    var collection: CollectionDetails = collectionFixture(),
    var failure: Throwable? = null,
    var may: Boolean = true,
    var failBlock: Throwable? = null,
) : CollectionRepository {
    val reads = mutableListOf<Int>()
    val blocks = mutableListOf<Boolean>()

    override suspend fun mayBlock() = may

    override suspend fun serverProfile() = null

    override suspend fun setBlocked(
        id: Int,
        blocked: Boolean,
    ) {
        failBlock?.let { throw it }
        blocks += blocked
        collection =
            collection.copy(
                parts =
                    collection.parts.map {
                        it.copy(availability = if (blocked) TitleAvailability.Blocklisted else TitleAvailability.None)
                    },
            )
    }

    override suspend fun mayRequest() = may

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
        val vm = CollectionPageViewModel(repository, id = 2344)

        assertEquals(listOf(2344), repository.reads)
        assertEquals("The Collection", (vm.uiState.value as CollectionPageUiState.Ready).collection.name)
    }

    @Test
    fun `a failed read is an error that retries`() {
        val repository = FakeCollections(failure = IOException("down"))
        val vm = CollectionPageViewModel(repository, id = 2344)
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

    private fun CollectionPageViewModel.ready() = uiState.value as CollectionPageUiState.Ready

    @Test
    fun `a viewer who may request can open the sheet while a film is open`() {
        val vm = CollectionPageViewModel(FakeCollections(), id = 2344)

        vm.openRequestSheet()

        assertTrue(vm.ready().requestSheetOpen)
    }

    @Test
    fun `a viewer who may not request, or a collection with nothing open, cannot`() {
        val may = CollectionPageViewModel(FakeCollections(may = false), id = 2344)
        may.openRequestSheet()
        assertFalse(may.ready().requestSheetOpen)

        val none =
            CollectionPageViewModel(
                FakeCollections(collection = collectionFixture(listOf(partFixture(1, TitleAvailability.Available)))),
                id = 2344,
            )
        none.openRequestSheet()
        assertFalse(none.ready().requestSheetOpen)
    }

    @Test
    fun `a request that went through closes the sheet says so and reads the collection again`() =
        runTest {
            val repository = FakeCollections()
            val vm = CollectionPageViewModel(repository, id = 2344)
            vm.openRequestSheet()
            repository.collection =
                collectionFixture(listOf(partFixture(1, TitleAvailability.Pending), partFixture(2, TitleAvailability.Pending)))

            val event = awaitEvent(vm.events)
            vm.requested(RequestResult.Created)

            assertEquals(CollectionPageEvent.Requested(RequestResult.Created), event.await())
            assertFalse(vm.ready().requestSheetOpen)
            assertEquals(0, vm.ready().collection.toRequest)
        }

    fun `a viewer who may not block is offered nothing`() {
        val vm = CollectionPageViewModel(FakeCollections(may = false), id = 2344)
        vm.askBlock()

        assertFalse(vm.ready().mayBlock)
        assertFalse(vm.ready().confirmingBlock)
    }

    @Test
    fun `blocking asks first and sends once confirmed then says so and shows the films blocked`() =
        runTest {
            val repository = FakeCollections()
            val vm = CollectionPageViewModel(repository, id = 2344)
            vm.askBlock()
            assertTrue(vm.ready().confirmingBlock)
            assertTrue(repository.blocks.isEmpty())

            val event = awaitEvent(vm.events)
            vm.setBlocked(true)

            assertEquals(CollectionPageEvent.Changed(true), event.await())
            assertEquals(listOf(true), repository.blocks)
            assertTrue(vm.ready().collection.blocked)
            assertFalse(vm.ready().confirmingBlock)
            assertFalse(vm.ready().changing)
        }

    @Test
    fun `unblocking goes straight through`() =
        runTest {
            val repository = FakeCollections(collection = collectionFixture(listOf(partFixture(1, TitleAvailability.Blocklisted))))
            val vm = CollectionPageViewModel(repository, id = 2344)
            vm.askBlock()
            assertFalse(vm.ready().confirmingBlock)

            val event = awaitEvent(vm.events)
            vm.setBlocked(false)

            assertEquals(CollectionPageEvent.Changed(false), event.await())
            assertFalse(vm.ready().collection.blocked)
        }

    @Test
    fun `a refused block is a failure event and leaves the page as it was`() =
        runTest {
            val vm = CollectionPageViewModel(FakeCollections(failBlock = IOException("down")), id = 2344)

            val event = awaitEvent(vm.events)
            vm.setBlocked(true)

            assertTrue(event.await() is CollectionPageEvent.Failed)
            assertFalse(vm.ready().collection.blocked)
            assertFalse(vm.ready().changing)
        }

    @Test
    fun `a block that went through is counted and leaves a breadcrumb`() =
        runTest {
            val analytics = RecordingAnalytics()
            val breadcrumbs = RecordingCrashBreadcrumbs()
            val vm = CollectionPageViewModel(FakeCollections(), analytics, breadcrumbs, 2344)

            val event = awaitEvent(vm.events)
            vm.setBlocked(true)
            event.await()

            assertEquals(
                listOf(
                    AnalyticsEvents.BLOCKLIST_CHANGED to mapOf<String, Any>(AnalyticsEvents.PARAM_ACTION to "collection_blocked"),
                ),
                analytics.events,
            )
            assertEquals(listOf("collection_id" to "2344"), breadcrumbs.keys)
        }

    @Test
    fun `an unblock is counted under its own action`() =
        runTest {
            val analytics = RecordingAnalytics()
            val repository = FakeCollections(collection = collectionFixture(listOf(partFixture(1, TitleAvailability.Blocklisted))))
            val vm = CollectionPageViewModel(repository, analytics, NoOpCrashBreadcrumbs, 2344)

            val event = awaitEvent(vm.events)
            vm.setBlocked(false)
            event.await()

            assertEquals("collection_unblocked", analytics.events.single().second[AnalyticsEvents.PARAM_ACTION])
        }

    @Test
    fun `a refused block reports the failed operation`() =
        runTest {
            val analytics = RecordingAnalytics()
            val vm =
                CollectionPageViewModel(
                    FakeCollections(failBlock = HttpException(Response.error<Unit>(500, "".toResponseBody()))),
                    analytics,
                    NoOpCrashBreadcrumbs,
                    2344,
                )

            val event = awaitEvent(vm.events)
            vm.setBlocked(true)
            event.await()

            assertEquals("block_collection", analytics.events.single().second[AnalyticsEvents.PARAM_OPERATION])
        }

    @Test
    fun `blocking reaches held films only when a film is held or requested`() {
        assertFalse(collectionFixture(listOf(partFixture(1), partFixture(2, TitleAvailability.Blocklisted))).blockReachesHeldFilms)
        assertTrue(collectionFixture(listOf(partFixture(1), partFixture(2, TitleAvailability.Available))).blockReachesHeldFilms)
        assertTrue(collectionFixture(listOf(partFixture(1, TitleAvailability.Pending))).blockReachesHeldFilms)
    }
}
