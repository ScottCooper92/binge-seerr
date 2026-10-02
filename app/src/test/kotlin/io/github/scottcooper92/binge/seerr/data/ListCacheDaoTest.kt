package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The paging, status and clear queries behind the issue, request and user mediators, against the
 * real database. The fakes these stores use elsewhere reimplement the same rules in Kotlin, so only
 * this proves the SQL means what they do.
 */
@RunWith(RobolectricTestRunner::class)
class ListCacheDaoTest {
    @get:Rule
    val database = CacheDatabaseRule()

    private val db get() = database.db

    private fun issue(
        listKey: String,
        id: Int,
        orderIndex: Int,
        status: String = "OPEN",
    ) = IssueEntity(
        listKey = listKey,
        id = id,
        tmdbId = id,
        mediaType = "movie",
        title = "I$id",
        posterUrl = null,
        year = null,
        issueType = "VIDEO",
        status = status,
        reportedBy = null,
        reportedById = null,
        commentCount = 0,
        createdAtMillis = null,
        updatedAtMillis = null,
        problem = null,
        problemSeason = null,
        problemEpisode = null,
        orderIndex = orderIndex,
    )

    private fun request(
        listKey: String,
        id: Int,
        orderIndex: Int,
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
        requestedById = null,
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

    private fun user(
        listKey: String,
        id: Int,
        orderIndex: Int,
        permissions: Int = 0,
    ) = UserEntity(
        listKey = listKey,
        id = id,
        name = "U$id",
        email = null,
        handle = null,
        avatarUrl = null,
        origin = "LOCAL",
        permissions = permissions,
        requestCount = 0,
        createdAtMillis = null,
        orderIndex = orderIndex,
    )

    private suspend fun <T : Any> PagingSource<Int, T>.rows(): List<T> =
        (
            load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = PAGE, placeholdersEnabled = false),
            ) as PagingSource.LoadResult.Page
        ).data

    @Test
    fun `issues page in server order, one list's slice at a time`() =
        runBlocking {
            db.issueDao().upsertAll(
                listOf(issue("all", 3, 2), issue("all", 1, 0), issue("all", 2, 1), issue("open", 9, 0)),
            )

            assertEquals(
                listOf(1, 2, 3),
                db
                    .issueDao()
                    .pagingSource("all", status = null)
                    .rows()
                    .map { it.id },
            )
            assertEquals(
                listOf(9),
                db
                    .issueDao()
                    .pagingSource("open", status = null)
                    .rows()
                    .map { it.id },
            )
        }

    @Test
    fun `an issue resolved on one page leaves the open list in every slice that cached it`() =
        runBlocking {
            db.issueDao().upsertAll(
                listOf(issue("all", 1, 0), issue("all", 2, 1), issue("open", 1, 0), issue("open", 2, 1)),
            )

            db.issueDao().updateStatus(id = 1, status = "RESOLVED")

            assertEquals(
                listOf(2),
                db
                    .issueDao()
                    .pagingSource("open", status = "OPEN")
                    .rows()
                    .map { it.id },
            )
            assertEquals(
                listOf(1, 2),
                db
                    .issueDao()
                    .pagingSource("all", status = null)
                    .rows()
                    .map { it.id },
            )
            assertEquals("RESOLVED", db.issueDao().byId(1)?.status)
        }

    @Test
    fun `issue upsert replaces the same list and id, delete removes every copy, clear is per list`() =
        runBlocking {
            val dao = db.issueDao()
            dao.upsertAll(listOf(issue("all", 1, 0), issue("open", 1, 0), issue("open", 2, 1)))
            dao.upsertAll(listOf(issue("all", 1, 5)))

            assertEquals(listOf(5), dao.pagingSource("all", status = null).rows().map { it.orderIndex })

            dao.clear("open")
            assertEquals(emptyList<Int>(), dao.pagingSource("open", status = null).rows().map { it.id })
            assertEquals(listOf(1), dao.pagingSource("all", status = null).rows().map { it.id })

            dao.upsertAll(listOf(issue("open", 1, 0)))
            dao.delete(1)
            assertNull(dao.byId(1))

            dao.upsertAll(listOf(issue("all", 7, 0)))
            dao.clearAll()
            assertNull(dao.byId(7))
        }

    @Test
    fun `requests page in server order and a status change reaches every slice`() =
        runBlocking {
            val dao = db.requestDao()
            dao.upsertAll(listOf(request("pending", 2, 1), request("pending", 1, 0), request("all", 1, 0)))

            assertEquals(listOf(1, 2), dao.pagingSource("pending").rows().map { it.id })

            dao.updateStatus(id = 1, status = 2)

            assertEquals(
                listOf(2, 2),
                listOf("pending", "all").map { key ->
                    dao
                        .pagingSource(key)
                        .rows()
                        .first { it.id == 1 }
                        .status
                },
            )
        }

    @Test
    fun `request delete and clear remove what they say and nothing else`() =
        runBlocking {
            val dao = db.requestDao()
            dao.upsertAll(listOf(request("pending", 1, 0), request("pending", 2, 1), request("all", 1, 0)))

            dao.delete(2)
            assertEquals(listOf(1), dao.pagingSource("pending").rows().map { it.id })

            dao.clear("pending")
            assertEquals(emptyList<Int>(), dao.pagingSource("pending").rows().map { it.id })
            assertEquals(1, dao.byId(1)?.id)

            dao.clearAll()
            assertNull(dao.byId(1))
        }

    @Test
    fun `users page in server order and permissions are read and written across every order`() =
        runBlocking {
            val dao = db.userDao()
            dao.upsertAll(
                listOf(user("created", 2, 1, permissions = 2), user("created", 1, 0, permissions = 4), user("name", 1, 3, permissions = 4)),
            )

            assertEquals(listOf(1, 2), dao.pagingSource("created").rows().map { it.id })
            assertEquals(listOf(4, 4), dao.permissionsFor(listOf(1)).map { it.permissions })

            dao.updatePermissions(ids = listOf(1, 2), permissions = 8)

            assertEquals(
                listOf(1 to 8, 1 to 8, 2 to 8),
                dao.permissionsFor(listOf(1, 2)).map { it.id to it.permissions }.sortedBy { it.first },
            )
            assertEquals(emptyList<UserPermissionsRow>(), dao.permissionsFor(listOf(99)))
        }

    @Test
    fun `user delete and clear remove what they say and nothing else`() =
        runBlocking {
            val dao = db.userDao()
            dao.upsertAll(listOf(user("created", 1, 0), user("created", 2, 1), user("name", 1, 0)))

            dao.delete(2)
            assertEquals(listOf(1), dao.pagingSource("created").rows().map { it.id })

            dao.clear("created")
            assertEquals(emptyList<Int>(), dao.pagingSource("created").rows().map { it.id })
            assertEquals(listOf(1), dao.pagingSource("name").rows().map { it.id })

            dao.clearAll()
            assertNull(dao.byId(1))
        }

    @Test
    fun `a remote key reads null until written, null when the last page is cached, and clears together`() =
        runBlocking {
            val issues = db.issueRemoteKeyDao()
            val requests = db.requestRemoteKeyDao()
            val users = db.userRemoteKeyDao()

            assertNull(issues.nextSkip("all"))
            issues.upsert(IssueRemoteKeyEntity("all", nextSkip = 20))
            requests.upsert(RequestRemoteKeyEntity("all", nextSkip = 40))
            users.upsert(UserRemoteKeyEntity("all", nextSkip = 60))
            assertEquals(20, issues.nextSkip("all"))

            issues.upsert(IssueRemoteKeyEntity("all", nextSkip = null))
            assertNull(issues.nextSkip("all"))

            issues.clearAll()
            requests.clearAll()
            users.clearAll()
            assertNull(requests.nextSkip("all"))
            assertNull(users.nextSkip("all"))
        }

    @Test
    fun `the media status cache replaces on the same title and clears`() =
        runBlocking {
            val dao = db.mediaStatusDao()
            dao.upsert(MediaStatusEntity(mediaType = 1, tmdbId = 5, status = "old", fetchedAtMillis = 1, requesterIds = ""))
            dao.upsert(MediaStatusEntity(mediaType = 1, tmdbId = 5, status = "new", fetchedAtMillis = 2, requesterIds = "1:2"))

            assertEquals("new", dao.find(1, 5)?.status)
            assertNull(dao.find(2, 5))

            dao.clear()
            assertNull(dao.find(1, 5))
        }

    private companion object {
        const val PAGE = 50
    }
}
