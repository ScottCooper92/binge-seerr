package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch

private const val ADMIN = 2
private const val CREATE_ISSUES = 1 shl 22

/** The issue page over a real connection into a path-scripted Seerr; Main is real-time, as for the hub. */
class IssueDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
    private val responses = mutableMapOf<String, (RecordedRequest) -> MockResponse>()
    private val viewModels = ViewModelStore()
    private var stores = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return responses[request.method + " " + request.url.encodedPath]?.invoke(request)
                        ?: responses["* " + request.url.encodedPath]?.invoke(request)
                        ?: MockResponse(code = 404)
                }
            }
        seerr.start()
    }

    /**
     * Main is set on every setup and never reset: a callback still in flight at teardown would
     * otherwise dispatch into the unset window and be reported into whichever test runs next.
     */
    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private fun serve(
        key: String,
        body: String,
        code: Int = 200,
    ) {
        responses[key] = { MockResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body) }
    }

    private fun issueJson(
        comments: String = """[{"id":1,"message":"Audio out of sync","user":{"id":8,"displayName":"ana"}},
            {"id":2,"message":"Same here","user":{"id":9,"displayName":"bo","permissions":2}}]""",
    ) = """{"id":31,"issueType":2,"status":1,"createdAt":"2026-06-01T10:00:00.000Z","createdBy":{"id":8,"displayName":"ana"},
            "media":{"id":900,"tmdbId":100,"mediaType":"movie","mediaUrl":"https://jellyfin.example.com/item/1"},
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

    private suspend fun TestScope.viewModel(): IssueDetailViewModel {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d${stores++}.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = IssueDetailViewModel(connection, TitleCache(), 31)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun IssueDetailViewModel.awaitReady(match: (IssueDetailUiState.Ready) -> Boolean = { true }): IssueDetailUiState.Ready =
        uiState.first { it is IssueDetailUiState.Ready && match(it) } as IssueDetailUiState.Ready

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
            assertEquals(seerr.url("/").toString() + "issues/31", detail.webUrl)
            assertEquals("https://jellyfin.example.com/item/1", detail.mediaServerUrl)
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
            val posted =
                received
                    .last { it.method == "POST" }
                    .body
                    ?.utf8()
                    .orEmpty()
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

    @Test
    fun `editing a pending comment mid-send cancels the original post so only the edit lands`() =
        runTest {
            server(ADMIN)
            // A CompletableDeferred, not a blocking latch: awaiting it must not stall the test's own
            // coroutine machinery, which is what a real blocking wait here would do.
            val firstPostReceived = CompletableDeferred<Unit>()
            val releaseFirstPost = CountDownLatch(1)
            var postCount = 0
            responses["POST /api/v1/issue/31/comment"] = {
                val n = ++postCount
                if (n == 1) {
                    firstPostReceived.complete(Unit)
                    releaseFirstPost.await()
                }
                MockResponse(
                    code = 200,
                    headers = headersOf("Content-Type", "application/json"),
                    body =
                        issueJson(
                            comments =
                                """[{"id":1,"message":"Audio out of sync"},
                                   {"id":${if (n == 1) 9 else 10},"message":"x","user":{"id":7}}]""",
                        ),
                )
            }
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft("original")
            vm.postComment()
            val pending = vm.awaitReady { it.outbox.singleOrNull()?.state == SendState.Sending }
            firstPostReceived.await()

            vm.editOutbox(pending.outbox.single().localId, "edited")
            releaseFirstPost.countDown()

            val landed = vm.awaitReady { it.outbox.isEmpty() && it.detail.comments.any { c -> c.id == 10 } }
            assertFalse(landed.detail.comments.any { it.id == 9 })
        }

    @Test
    fun `discarding a pending comment mid-send cancels the send so it cannot resurrect`() =
        runTest {
            server(ADMIN)
            val postReceived = CompletableDeferred<Unit>()
            val releasePost = CountDownLatch(1)
            responses["POST /api/v1/issue/31/comment"] = {
                postReceived.complete(Unit)
                releasePost.await()
                MockResponse(
                    code = 200,
                    headers = headersOf("Content-Type", "application/json"),
                    body =
                        issueJson(
                            comments = """[{"id":1,"message":"Audio out of sync"},{"id":9,"message":"gone","user":{"id":7}}]""",
                        ),
                )
            }
            val vm = viewModel()
            vm.awaitReady()

            vm.setDraft("gone")
            vm.postComment()
            val pending = vm.awaitReady { it.outbox.singleOrNull()?.state == SendState.Sending }
            postReceived.await()

            vm.dropOutbox(pending.outbox.single().localId)
            releasePost.countDown()

            val settled = vm.awaitReady { it.outbox.isEmpty() }
            assertFalse(settled.detail.comments.any { it.id == 9 })
        }

    @Test
    fun `a write that succeeds but fails to reload reports failure, not success, leaving the stale comment`() =
        runTest {
            server(ADMIN)
            serve("PUT /api/v1/issueComment/2", "{}")
            var issueCalls = 0
            responses["GET /api/v1/issue/31"] = {
                if (++issueCalls == 1) {
                    MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = issueJson())
                } else {
                    MockResponse(code = 503)
                }
            }
            val vm = viewModel()
            vm.awaitReady()

            vm.editComment(2, "Same here, fixed now")

            assertTrue(vm.events.first() is IssueDetailEvent.Failed)
            val ready = vm.awaitReady { it.commentAction == CommentAction.None }
            assertEquals(
                "Same here",
                ready.detail.comments
                    .single()
                    .message,
            )
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
            assertEquals(IssueDetailEvent.CommentEdited, vm.events.first())
            assertTrue(received.any { it.method == "PUT" && it.body?.utf8()?.contains("fixed now") == true })

            vm.deleteComment(2)
            assertEquals(IssueDetailEvent.CommentDeleted, vm.events.first())
            assertTrue(received.any { it.method == "DELETE" && it.url.encodedPath == "/api/v1/issueComment/2" })
            assertEquals(CommentAction.None, vm.awaitReady { it.commentAction == CommentAction.None }.commentAction)
        }

    @Test
    fun `a reporter may comment on their own issue and act on their own comment only`() =
        runTest {
            server(CREATE_ISSUES, userId = 8)
            val vm = viewModel()

            val detail = vm.awaitReady().detail

            assertTrue(detail.canComment)
            assertFalse(detail.canManage)
            assertTrue(detail.report?.isMine == true)
            assertTrue(detail.canActOn(checkNotNull(detail.report)))
            assertFalse(detail.canActOn(detail.comments.single()))

            server(CREATE_ISSUES, userId = 9)
            assertFalse(viewModel().awaitReady().detail.canComment)
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
