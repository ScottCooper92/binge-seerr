package com.cooper.binge.companion.seerr.data

import androidx.paging.PagingSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A request the server has just made is in the opened lists it belongs in, and in no other, with no refresh. */
@RunWith(RobolectricTestRunner::class)
class RequestStoreAddCreatedTest {
    @get:Rule
    val database = CacheDatabaseRule()

    private fun row(
        listKey: String,
        id: Int,
        orderIndex: Int,
        requestedById: Int? = 7,
        status: Int? = 1,
    ) = RequestEntity(
        listKey = listKey,
        id = id,
        tmdbId = id,
        mediaType = "movie",
        title = "R$id",
        posterUrl = null,
        year = null,
        requestedBy = null,
        requestedById = requestedById,
        requestedAtMillis = null,
        status = status,
        mediaStatus = null,
        downloadFraction = null,
        downloadEtaMinutes = null,
        downloading = false,
        seasonNumbers = "",
        is4k = false,
        orderIndex = orderIndex,
    )

    private suspend fun RoomRequestStore.ids(listKey: String): List<Int> =
        (
            pagingSource(listKey).load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = PAGE, placeholdersEnabled = false),
            ) as PagingSource.LoadResult.Page
        ).data.map { it.id }

    @Test
    fun `a new request leads every opened list it belongs in`() =
        runBlocking {
            val store = RoomRequestStore(database.db)
            listOf("all:added:all", "pending:added:all", "all:added:7").forEach { key ->
                store.refresh(key, listOf(row(key, 1, 0), row(key, 2, 1)), nextSkip = null)
            }

            store.addCreated(row("", 9, 0))

            assertEquals(listOf(9, 1, 2), store.ids("all:added:all"))
            assertEquals(listOf(9, 1, 2), store.ids("pending:added:all"))
            assertEquals(listOf(9, 1, 2), store.ids("all:added:7"))
        }

    @Test
    fun `a list it does not belong in is left to its refresh`() =
        runBlocking {
            val store = RoomRequestStore(database.db)
            listOf("approved:added:all", "all:added:5", "all:requested:all", "available:added:all").forEach { key ->
                store.refresh(key, listOf(row(key, 1, 0)), nextSkip = null)
            }

            store.addCreated(row("", 9, 0))

            assertEquals(listOf(1), store.ids("approved:added:all"))
            assertEquals(listOf(1), store.ids("all:added:5"))
            assertEquals(listOf(1), store.ids("all:requested:all"))
            assertEquals(listOf(1), store.ids("available:added:all"))
        }

    @Test
    fun `an auto-approved request goes in the approved list and not the pending one`() =
        runBlocking {
            val store = RoomRequestStore(database.db)
            listOf("approved:added:all", "pending:added:all").forEach { key ->
                store.refresh(key, listOf(row(key, 1, 0)), nextSkip = null)
            }

            store.addCreated(row("", 9, 0, status = 2))

            assertEquals(listOf(9, 1), store.ids("approved:added:all"))
            assertEquals(listOf(1), store.ids("pending:added:all"))
        }

    private companion object {
        const val PAGE = 50
    }
}
