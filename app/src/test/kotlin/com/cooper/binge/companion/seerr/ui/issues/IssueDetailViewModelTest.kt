package com.cooper.binge.companion.seerr.ui.issues

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.FakeIssueStore
import com.cooper.binge.companion.seerr.data.IssueEntity
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.ui.MediaServerItem
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.FakeTitleDao
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import com.cooper.binge.companion.seerr.util.RecordingCrashBreadcrumbs
import com.cooper.binge.companion.seerr.util.afterProcessDeath
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

private const val ADMIN = 2
private const val CREATE_ISSUES = 1 shl 22

/** The issue page over an in-memory connection into a path-scripted Seerr. */
class IssueDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val received = CopyOnWriteArrayList<FakeRequest>()
    private val responses = mutableMapOf<String, (FakeRequest) -> FakeResponse>()
    private val viewModels = ViewModelStore()
    private var stores = 0
    private val cache = FakeIssueStore()
    private val analytics = RecordingAnalytics()
    private val crashBreadcrumbs = RecordingCrashBreadcrumbs()

    @Before
    fun setUp() {
        seerr.dispatcher = { request ->
            received += request
            responses[request.method + " " + request.url.encodedPath]?.invoke(request)
                ?: responses["* " + request.url.encodedPath]?.invoke(request)
                ?: FakeResponse(code = 404)
        }
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
    }

    private fun serve(
        key: String,
        body: String,
        code: Int = 200,
    ) {
        responses[key] = { FakeResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body) }
    }

    private fun issueJson(
        comments: String = """[{"id":1,"message":"Audio out of sync","user":{"id":8,"displayName":"ana"}},
            {"id":2,"message":"Same here","user":{"id":9,"displayName":"bo","permissions":2}}]""",
    ) = """{"id":31,"issueType":2,"status":1,"createdAt":"2026-06-01T10:00:00.000Z","createdBy":{"id":8,"displayName":"ana"},
            "media":{"id":900,"tmdbId":100,"mediaType":"movie","mediaUrl":"https://jellyfin.example.com/item/1","jellyfinMediaId":"a1b2"},
            "comments":$comments}"""

    private fun server(
        permissions: Int,
        userId: Int = 7,
    ) {
        serve("* /api/v1/auth/me", """{"id":$userId,"displayName":"Scott","permissions":$permissions}""")
        serve("* /api/v1/status", """{"version":"3.1.0"}""")
        serve("* /api/v1/settings/public", """{"mediaServerType":2}""")
        serve("GET /api/v1/issue/31", issueJson())
        serve("* /api/v1/movie/100", """{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
    }

    private suspend fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): IssueDetailViewModel {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d${stores++}.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
            )
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm =
            IssueDetailViewModel(
                connection,
                TitleCache(FakeTitleDao()),
                cache,
                mainDispatcherRule.dispatcher,
                31,
                analytics,
                crashBreadcrumbs,
                savedState,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun IssueDetailViewModel.awaitReady(match: (IssueDetailUiState.Ready) -> Boolean = { true }): IssueDetailUiState.Ready =
        uiState.first { it is IssueDetailUiState.Ready && match(it) } as IssueDetailUiState.Ready

    /** #1239: the thread's "2 minutes ago" ages while the page shows, and the clock stops when it leaves. */
    @Test
    fun `the thread's clock moves on each minute while the page is showing, and stops when it leaves`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            var now = 1_789_275_660_000L
            vm.clock = { now }
            vm.awaitReady()
            vm.setScreenVisible(true)
            vm.awaitReady { it.now == now }

            now += 60_000L
            testScheduler.advanceTimeBy(60_001L)
            vm.awaitReady { it.now == now }

            vm.setScreenVisible(false)
            val left = now
            now += 60_000L
            testScheduler.advanceTimeBy(60_001L)
            assertEquals(left, vm.awaitReady().now)
        }

    /** #1026: a comment half typed when the process is killed comes back in the composer. */
    @Test
    fun `a half-typed comment survives the process being killed`() =
        runTest {
            server(ADMIN)
            val savedState = SavedStateHandle()
            val vm = viewModel(savedState)
            vm.awaitReady()
            vm.setDraft("Still no audio on")

            val back = viewModel(savedState.afterProcessDeath())

            assertEquals("Still no audio on", back.awaitReady().draft)
        }

    @Test
    fun `the code for a phone is written under the application url, and the page's own link stays on the address in use`() =
        runTest {
            server(ADMIN)
            serve("* /api/v1/settings/public", """{"mediaServerType":2,"applicationUrl":"https://seerr.tail1234.ts.net/"}""")
            val vm = viewModel()

            val detail = vm.awaitReady().detail

            assertEquals(seerr.url("/") + "issues/31", detail.webUrl)
            assertEquals("https://seerr.tail1234.ts.net/issues/31", detail.phoneUrl)
        }

    @Test
    fun `the code for a phone falls back to the address in use when the application url is blank or malformed`() =
        runTest {
            server(ADMIN)
            serve("* /api/v1/settings/public", """{"mediaServerType":2,"applicationUrl":"seerr.example.com"}""")
            val vm = viewModel()

            assertEquals(seerr.url("/") + "issues/31", vm.awaitReady().detail.phoneUrl)
        }

    @Test
    fun `the page reads as the title, the report, the thread with its authors, and what a manager may do`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()

            val detail = vm.awaitReady().detail

            assertEquals("Heat", detail.item.title)
            assertEquals(IssueType.Audio, detail.item.type)
            assertEquals("Audio out of sync", detail.report?.message)
            assertEquals("ana", detail.report?.author)
            val reply = detail.comments.single()
            assertEquals("bo", reply.author)
            assertTrue(reply.isAdmin)
            assertFalse(reply.isMine)
            assertTrue(detail.canComment)
            assertTrue(detail.canManage)
            assertTrue(detail.canActOn(reply))
            assertEquals(seerr.url("/") + "issues/31", detail.webUrl)
            assertEquals("https://jellyfin.example.com/item/1", detail.mediaServerUrl)
            assertEquals(MediaServerItem(SeerrMediaServer.Jellyfin, "a1b2"), detail.mediaServerItem)
        }

    @Test
    fun `a posted comment shows at once, is replaced by the server's copy, and a failed one waits in the outbox`() =
        runTest {
            server(ADMIN)
            serve(
                "POST /api/v1/issue/31/comment",
                issueJson(
                    comments =
                        """[{"id":1,"message":"Audio out of sync"},
                           {"id":5,"message":"On it","user":{"id":7,"displayName":"Scott"}}]""",
                ),
            )
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft(" On it ")
            vm.postComment()

            val confirmed = vm.awaitReady { it.outbox.isEmpty() && it.detail.comments.any { c -> c.id == 5 } }
            assertEquals("", confirmed.draft)
            val mine = confirmed.detail.comments.last()
            assertEquals("On it", mine.message)
            assertTrue(mine.isMine)
            val posted = received.last { it.method == "POST" }.body
            assertTrue(posted, posted.contains("\"message\":\"On it\""))

            serve("POST /api/v1/issue/31/comment", "", code = 503)
            vm.setDraft("Still broken")
            vm.postComment()
            val failed = vm.awaitReady { it.outbox.singleOrNull()?.state is SendState.Failed }
            assertEquals(SendState.Failed(retryable = true), failed.outbox.single().state)
            assertEquals("Scott", failed.outbox.single().author)

            serve(
                "POST /api/v1/issue/31/comment",
                issueJson(comments = """[{"id":1,"message":"Audio out of sync"},{"id":6,"message":"Still broken","user":{"id":7}}]"""),
            )
            vm.retryOutbox(failed.outbox.single().localId)
            val landed = vm.awaitReady { it.outbox.isEmpty() && it.detail.comments.any { c -> c.id == 6 } }
            assertEquals(
                "Still broken",
                landed.detail.comments
                    .last()
                    .message,
            )
        }

    /** A refusal on the merits would only be refused again, so the outbox does not offer to resend it (#1465). */
    @Test
    fun `a comment the server refuses waits in the outbox without a retry`() =
        runTest {
            server(ADMIN)
            serve("POST /api/v1/issue/31/comment", """{"message":"Comment too long"}""", code = 400)
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft("Still broken")
            vm.postComment()

            val failed = vm.awaitReady { it.outbox.singleOrNull()?.state is SendState.Failed }
            assertEquals(SendState.Failed(retryable = false), failed.outbox.single().state)
        }

    @Test
    fun `a comment landing on the same issue while a post is in flight is not mistaken for the one just posted`() =
        runTest {
            server(ADMIN)
            serve(
                "POST /api/v1/issue/31/comment",
                issueJson(
                    comments =
                        """[{"id":1,"message":"Audio out of sync"},{"id":2,"message":"Same here"},
                           {"id":5,"message":"On it","user":{"id":7,"displayName":"Scott"}},
                           {"id":6,"message":"Unrelated","user":{"id":9,"displayName":"bo"}}]""",
                ),
            )
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft("On it")
            vm.postComment()

            val confirmed = vm.awaitReady { it.outbox.isEmpty() && it.detail.comments.any { c -> c.id == 5 } }
            assertFalse(confirmed.detail.comments.any { it.id == 6 })
            val mine = confirmed.detail.comments.last()
            assertEquals("On it", mine.message)
            assertTrue(mine.isMine)
        }

    @Test
    fun `two outbox entries sending identical text concurrently are not both matched to the same server comment`() =
        runTest {
            server(ADMIN)
            val firstPostReceived = CompletableDeferred<Unit>()
            val releaseFirstPost = CountDownLatch(1)
            var postCount = 0
            responses["POST /api/v1/issue/31/comment"] = {
                val n = ++postCount
                if (n == 1) {
                    firstPostReceived.complete(Unit)
                    releaseFirstPost.await()
                }
                FakeResponse(
                    code = 200,
                    headers = headersOf("Content-Type", "application/json"),
                    body =
                        issueJson(
                            comments =
                                """[{"id":1,"message":"Audio out of sync"},
                                   {"id":9,"message":"hi","user":{"id":7}},
                                   {"id":10,"message":"hi","user":{"id":7}}]""",
                        ),
                )
            }
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft("hi")
            vm.postComment()
            vm.awaitReady { it.outbox.singleOrNull()?.state == SendState.Sending }
            firstPostReceived.await()

            vm.setDraft("hi")
            vm.postComment()
            val secondLanded = vm.awaitReady { it.outbox.size == 1 && it.detail.comments.any { c -> c.id == 9 || c.id == 10 } }
            assertEquals(1, secondLanded.detail.comments.count { it.id == 9 || it.id == 10 })

            releaseFirstPost.countDown()
            val bothLanded = vm.awaitReady { it.outbox.isEmpty() }
            assertEquals(
                setOf(9, 10),
                bothLanded.detail.comments
                    .filter { it.id == 9 || it.id == 10 }
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun `editing or discarding a comment whose send is in flight does nothing, and the one post lands`() =
        runTest {
            server(ADMIN)
            // A CompletableDeferred, not a blocking latch: awaiting it must not stall the test's own
            // coroutine machinery, which is what a real blocking wait here would do.
            val postReceived = CompletableDeferred<Unit>()
            val releasePost = CountDownLatch(1)
            var postCount = 0
            responses["POST /api/v1/issue/31/comment"] = {
                postCount++
                postReceived.complete(Unit)
                releasePost.await()
                FakeResponse(
                    code = 200,
                    headers = headersOf("Content-Type", "application/json"),
                    body =
                        issueJson(
                            comments = """[{"id":1,"message":"Audio out of sync"},{"id":9,"message":"original","user":{"id":7}}]""",
                        ),
                )
            }
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft("original")
            vm.postComment()
            val pending = vm.awaitReady { it.outbox.singleOrNull()?.state == SendState.Sending }
            postReceived.await()

            // Held on the wire: neither takes effect, so no second send and no vanished row.
            val localId = pending.outbox.single().localId
            vm.editOutbox(localId, "edited")
            vm.dropOutbox(localId)
            val held = vm.awaitReady()
            assertEquals("original", held.outbox.single().message)
            assertEquals(SendState.Sending, held.outbox.single().state)

            releasePost.countDown()
            val landed = vm.awaitReady { it.outbox.isEmpty() && it.detail.comments.any { c -> c.id == 9 } }
            assertEquals(1, postCount)
            assertEquals(1, landed.detail.comments.count { it.message == "original" })
        }

    @Test
    fun `a comment whose send failed can be edited and sent again, or discarded`() =
        runTest {
            server(ADMIN)
            var postCount = 0
            responses["POST /api/v1/issue/31/comment"] = {
                postCount++
                if (postCount == 1) {
                    FakeResponse(code = 500, headers = headersOf("Content-Type", "application/json"), body = "{}")
                } else {
                    FakeResponse(
                        code = 200,
                        headers = headersOf("Content-Type", "application/json"),
                        body =
                            issueJson(
                                comments = """[{"id":1,"message":"Audio out of sync"},{"id":10,"message":"edited","user":{"id":7}}]""",
                            ),
                    )
                }
            }
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft("original")
            vm.postComment()
            val failed = vm.awaitReady { it.outbox.singleOrNull()?.state is SendState.Failed }

            vm.editOutbox(failed.outbox.single().localId, "edited")
            val landed = vm.awaitReady { it.outbox.isEmpty() && it.detail.comments.any { c -> c.id == 10 } }
            assertEquals(2, postCount)
            assertEquals(1, landed.detail.comments.count { it.message == "edited" })
        }

    @Test
    fun `an edit that saves but fails to reload keeps the saved text and reports the reload`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}")
            var issueCalls = 0
            responses["GET /api/v1/issue/31"] = {
                if (++issueCalls == 1) {
                    FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = issueJson())
                } else {
                    FakeResponse(code = 503)
                }
            }
            val vm = viewModel()
            vm.awaitReady()

            val failed = awaitEvent(vm.events)
            vm.editComment(2, "Same here, fixed now")

            assertTrue(failed.await() is IssueDetailEvent.Failed)
            val ready = vm.awaitReady { it.commentAction == CommentAction.None }
            assertEquals(SaveStatus.Saved, ready.editStatus)
            assertTrue(ready.unsentEdits.isEmpty())
            assertEquals(
                "Same here, fixed now",
                ready.detail.comments
                    .single()
                    .message,
            )
        }

    @Test
    fun `a failed edit keeps the change on the page until a retry saves it`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}", code = 500)
            val vm = viewModel()
            vm.awaitReady()

            vm.editComment(2, "Same here, fixed now")
            val failed = vm.awaitReady { it.unsentEdits.isNotEmpty() }
            assertEquals(listOf(CommentEdit(2, "Same here, fixed now")), failed.unsentEdits)
            assertEquals(
                "Same here, fixed now",
                failed.detail.comments
                    .single()
                    .message,
            )

            serve("PUT /api/v1/issueComment/2", "{}")
            serve("GET /api/v1/issue/31", issueJson().replace("\"Same here\"", "\"Same here, fixed now\""))
            vm.editComment(2, "Same here, fixed now")
            val saved = vm.awaitReady { it.unsentEdits.isEmpty() && it.editStatus == SaveStatus.Saved }
            assertEquals(
                "Same here, fixed now",
                saved.detail.comments
                    .single()
                    .message,
            )
            assertEquals(2, received.count { it.method == "PUT" })
        }

    @Test
    fun `a second failed edit keeps the first owed, and a retry sends each in turn`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/1", "{}", code = 500)
            serve("PUT /api/v1/issueComment/2", "{}", code = 500)
            val vm = viewModel()
            vm.awaitReady()

            vm.editComment(1, "Audio drifts")
            vm.awaitReady { it.unsentEdits.size == 1 }
            vm.editComment(2, "Same here, fixed now")
            val failed = vm.awaitReady { it.unsentEdits.size == 2 && it.commentAction == CommentAction.None }
            assertEquals(listOf(CommentEdit(1, "Audio drifts"), CommentEdit(2, "Same here, fixed now")), failed.unsentEdits)
            assertEquals("Audio drifts", failed.detail.report?.message)

            serve("PUT /api/v1/issueComment/1", "{}")
            serve("GET /api/v1/issue/31", issueJson().replace("Audio out of sync", "Audio drifts"))
            vm.retryEdit()
            val afterFirst = vm.awaitReady { it.unsentEdits.size == 1 && it.editStatus == SaveStatus.Saved }
            assertEquals(listOf(CommentEdit(2, "Same here, fixed now")), afterFirst.unsentEdits)
            assertEquals(
                "Same here, fixed now",
                afterFirst.detail.comments
                    .single()
                    .message,
            )
            assertEquals("Audio drifts", afterFirst.detail.report?.message)

            serve("PUT /api/v1/issueComment/2", "{}")
            serve(
                "GET /api/v1/issue/31",
                issueJson().replace("Audio out of sync", "Audio drifts").replace("\"Same here\"", "\"Same here, fixed now\""),
            )
            vm.retryEdit()
            val afterSecond = vm.awaitReady { it.unsentEdits.isEmpty() && it.commentAction == CommentAction.None }
            assertTrue(afterSecond.unsentEdits.isEmpty())
            assertEquals(4, received.count { it.method == "PUT" })
        }

    @Test
    fun `a retry sends the newest text of an edit that failed twice`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}", code = 500)
            val vm = viewModel()
            vm.awaitReady()

            vm.editComment(2, "first try")
            vm.awaitReady { it.unsentEdits == listOf(CommentEdit(2, "first try")) && it.commentAction == CommentAction.None }
            vm.editComment(2, "second try")
            val failed = vm.awaitReady { it.unsentEdits == listOf(CommentEdit(2, "second try")) && it.commentAction == CommentAction.None }
            assertEquals(listOf(CommentEdit(2, "second try")), failed.unsentEdits)

            serve("PUT /api/v1/issueComment/2", "{}")
            serve("GET /api/v1/issue/31", issueJson().replace("\"Same here\"", "\"second try\""))
            vm.retryEdit()
            val saved = vm.awaitReady { it.unsentEdits.isEmpty() && it.editStatus == SaveStatus.Saved }
            assertEquals(
                "second try",
                saved.detail.comments
                    .single()
                    .message,
            )
        }

    @Test
    fun `a refused edit is reported, not kept for retry, and the thread returns to the server's text`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}", code = 404)
            val vm = viewModel()
            vm.awaitReady()

            val failed = awaitEvent(vm.events)
            vm.editComment(2, "Same here, fixed now")
            assertEquals(IssueDetailEvent.Failed(SeerrError.NotFound), failed.await())
            val ready = vm.awaitReady { it.commentAction == CommentAction.None && it.editStatus == SaveStatus.Idle }
            assertTrue(ready.unsentEdits.isEmpty())
            assertEquals(
                "Same here",
                ready.detail.comments
                    .single()
                    .message,
            )
        }

    @Test
    fun `a refused edit whose reload also fails leaves the thread on the text it had`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}", code = 403)
            val vm = viewModel()
            vm.awaitReady()
            serve("GET /api/v1/issue/31", "{}", code = 500)

            vm.editComment(2, "Same here, fixed now")
            val ready = vm.awaitReady { it.commentAction == CommentAction.None && it.editStatus == SaveStatus.Idle }
            assertTrue(ready.unsentEdits.isEmpty())
            assertEquals(
                "Same here",
                ready.detail.comments
                    .single()
                    .message,
            )
        }

    /** A refused retry puts back the server's text, not the owed text the page was showing, even when the reload fails. */
    @Test
    fun `a retry refused after a failed edit, with a failed reload, ends on the server's text with nothing owed`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}", code = 500)
            val vm = viewModel()
            vm.awaitReady()
            vm.editComment(2, "Same here, fixed now")
            val owed = vm.awaitReady { it.unsentEdits.isNotEmpty() }
            assertEquals(
                "Same here, fixed now",
                owed.detail.comments
                    .single()
                    .message,
            )

            serve("PUT /api/v1/issueComment/2", "{}", code = 401)
            serve("GET /api/v1/issue/31", "{}", code = 500)
            val failed = awaitEvent(vm.events)
            vm.retryEdit()

            assertTrue(failed.await() is IssueDetailEvent.Failed)
            val ready = vm.awaitReady { it.commentAction == CommentAction.None && it.editStatus == SaveStatus.Idle }
            assertTrue(ready.unsentEdits.isEmpty())
            assertEquals(
                "Same here",
                ready.detail.comments
                    .single()
                    .message,
            )
        }

    @Test
    fun `deleting a comment whose edit failed drops the edit that was owed to it`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}", code = 500)
            serve("DELETE /api/v1/issueComment/2", "{}")
            val vm = viewModel()
            vm.awaitReady()

            vm.editComment(2, "Same here, fixed now")
            vm.awaitReady { it.unsentEdits.isNotEmpty() }

            val commentDeleted = awaitEvent(vm.events)
            vm.deleteComment(2)
            assertEquals(IssueDetailEvent.CommentDeleted, commentDeleted.await())
            assertTrue(vm.awaitReady { it.commentAction == CommentAction.None }.unsentEdits.isEmpty())
        }

    @Test
    fun `editing and deleting a comment reload the thread and report the outcome`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}")
            serve("DELETE /api/v1/issueComment/2", "{}")
            val vm = viewModel()
            vm.awaitReady()

            vm.editComment(2, "Same here, fixed now")
            vm.awaitReady { it.editStatus == SaveStatus.Saved }
            assertTrue(received.any { it.method == "PUT" && it.body.contains("fixed now") })

            val commentDeleted = awaitEvent(vm.events)
            vm.deleteComment(2)
            assertEquals(IssueDetailEvent.CommentDeleted, commentDeleted.await())
            assertTrue(received.any { it.method == "DELETE" && it.url.encodedPath == "/api/v1/issueComment/2" })
            assertEquals(CommentAction.None, vm.awaitReady { it.commentAction == CommentAction.None }.commentAction)
            assertEquals(
                listOf("issue_commented" to mapOf("action" to "edited"), "issue_commented" to mapOf("action" to "deleted")),
                analytics.events,
            )
        }

    @Test
    fun `resolving moves the cached row with the page, and deleting removes both and pops`() =
        runTest {
            server(ADMIN)
            // The server's answer to the reload after each write is the state the write put it in.
            responses["POST /api/v1/issue/31/resolved"] = {
                serve("GET /api/v1/issue/31", issueJson().replace("\"status\":1", "\"status\":2"))
                json("{}")
            }
            responses["POST /api/v1/issue/31/open"] = {
                serve("GET /api/v1/issue/31", issueJson())
                json("{}")
            }
            serve("DELETE /api/v1/issue/31", "{}")
            cache.refresh("open:added", listOf(cachedRow(31, "Open"), cachedRow(32, "Open")), nextSkip = null)
            val vm = viewModel()
            assertTrue(vm.awaitReady().detail.canResolve)

            val issueResolved = awaitEvent(vm.events)
            vm.toggleStatus()
            assertEquals(IssueDetailEvent.IssueResolved, issueResolved.await())
            assertEquals("Resolved", cache.rows.first { it.id == 31 }.status)
            assertEquals("Open", cache.rows.first { it.id == 32 }.status)

            vm.awaitReady { it.action == IssueAction.None && it.detail.item.status == IssueStatus.Resolved }
            val issueReopened = awaitEvent(vm.events)
            vm.toggleStatus()
            assertEquals(IssueDetailEvent.IssueReopened, issueReopened.await())
            assertEquals("Open", cache.rows.first { it.id == 31 }.status)

            vm.awaitReady { it.action == IssueAction.None }
            val issueDeleted = awaitEvent(vm.events)
            vm.deleteIssue()
            assertEquals(IssueDetailEvent.IssueDeleted, issueDeleted.await())
            assertEquals(listOf(32), cache.rows.map { it.id })
            assertTrue(received.any { it.method == "DELETE" && it.url.encodedPath == "/api/v1/issue/31" })
            assertEquals(
                listOf(
                    "issue_moderated" to mapOf("action" to "resolved"),
                    "issue_moderated" to mapOf("action" to "reopened"),
                    "issue_moderated" to mapOf("action" to "deleted"),
                ),
                analytics.events,
            )
        }

    @Test
    fun `a status change that succeeds but fails to reload reports failure, not success, leaving the stale status`() =
        runTest {
            server(ADMIN)
            serve("POST /api/v1/issue/31/resolved", "{}")
            var issueCalls = 0
            responses["GET /api/v1/issue/31"] = {
                if (++issueCalls == 1) {
                    FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = issueJson())
                } else {
                    FakeResponse(code = 503)
                }
            }
            val vm = viewModel()
            vm.awaitReady()

            val failed = awaitEvent(vm.events)
            vm.toggleStatus()

            assertTrue(failed.await() is IssueDetailEvent.Failed)
            val ready = vm.awaitReady { it.action == IssueAction.None }
            assertEquals(IssueStatus.Open, ready.detail.item.status)
        }

    @Test
    fun `a cached row seeds the header at once, and the fetch then fills the page in behind it`() =
        runTest {
            server(ADMIN)
            cache.refresh("open:added", listOf(cachedRow(31, "Open").copy(title = "Cached Heat")), nextSkip = null)
            val release = CountDownLatch(1)
            responses["GET /api/v1/issue/31"] = {
                release.await()
                FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = issueJson())
            }
            val vm = viewModel()

            val seeded = vm.uiState.first { it is IssueDetailUiState.Seeded } as IssueDetailUiState.Seeded
            assertEquals("Cached Heat", seeded.item.title)
            assertEquals(IssueType.Audio, seeded.item.type)
            assertEquals(IssueStatus.Open, seeded.item.status)
            assertNull(seeded.error)

            release.countDown()
            assertEquals(
                "Heat",
                vm
                    .awaitReady()
                    .detail.item.title,
            )
        }

    @Test
    fun `a failed refresh keeps the seeded header and reports the error`() =
        runTest {
            server(ADMIN)
            cache.refresh("open:added", listOf(cachedRow(31, "Open").copy(title = "Cached Heat")), nextSkip = null)
            serve("GET /api/v1/issue/31", "", code = 503)
            val vm = viewModel()

            val failed = vm.uiState.first { it is IssueDetailUiState.Seeded && it.error != null } as IssueDetailUiState.Seeded

            assertEquals("Cached Heat", failed.item.title)
            assertEquals(SeerrError.Server, failed.error)

            serve("GET /api/v1/issue/31", issueJson())
            vm.reload()
            assertEquals(
                "Heat",
                vm
                    .awaitReady()
                    .detail.item.title,
            )
        }

    @Test
    fun `with no cached row the page still starts as loading, and a failed fetch is the error screen`() =
        runTest {
            server(ADMIN)
            serve("GET /api/v1/issue/31", "", code = 503)
            val vm = viewModel()

            assertTrue(vm.uiState.first { it !is IssueDetailUiState.Loading } is IssueDetailUiState.Error)
        }

    private fun cachedRow(
        id: Int,
        status: String,
    ) = IssueEntity(
        listKey = "open:added",
        id = id,
        tmdbId = 100,
        mediaType = "Movie",
        title = "Heat",
        posterUrl = null,
        year = "1995",
        issueType = "Audio",
        status = status,
        reportedBy = "ana",
        reportedById = 8,
        commentCount = 1,
        createdAtMillis = null,
        updatedAtMillis = null,
        problem = null,
        problemSeason = null,
        problemEpisode = null,
        orderIndex = id,
    )

    @Test
    fun `a reporter may comment on their own issue and act on their own comment only`() =
        runTest {
            server(CREATE_ISSUES, userId = 8)
            val vm = viewModel()

            val detail = vm.awaitReady().detail

            assertTrue(detail.canComment)
            assertFalse(detail.canManage)
            assertTrue(detail.canResolve)
            // Someone has replied, so the server would refuse the reporter's delete (#1010).
            assertFalse(detail.canDelete)
            assertTrue(detail.report?.isMine == true)
            assertTrue(detail.canActOn(checkNotNull(detail.report)))
            assertFalse(detail.canActOn(detail.comments.single()))

            server(CREATE_ISSUES, userId = 9)
            val stranger = viewModel().awaitReady().detail
            assertFalse(stranger.canComment)
            assertFalse(stranger.canResolve)
            assertFalse(stranger.canDelete)
        }

    /** The server lets a reporter delete their own issue only while nobody has replied (#1010). */
    @Test
    fun `a reporter may delete their own issue only before anyone replies, and a manager may whenever`() =
        runTest {
            server(CREATE_ISSUES, userId = 8)
            val reportOnly = """[{"id":1,"message":"Audio out of sync","user":{"id":8,"displayName":"ana"}}]"""
            serve("GET /api/v1/issue/31", issueJson(comments = reportOnly))
            assertTrue(viewModel().awaitReady().detail.canDelete)

            // A manager may delete it with the replies there too.
            server(ADMIN, userId = 7)
            assertTrue(viewModel().awaitReady().detail.canDelete)
        }

    private fun json(body: String) = FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
