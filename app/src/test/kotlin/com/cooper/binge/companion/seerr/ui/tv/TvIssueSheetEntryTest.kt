package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.FakeIssueStore
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailViewModel
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.FakeTitleDao
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.binge.companion.seerr.util.createSeerrAndroidComposeRule
import com.cooper.homelab.core.testing.RecordingAnalytics
import com.cooper.homelab.core.testing.RecordingCrashBreadcrumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ADMIN = 2
private const val ISSUE_ID = 31

/**
 * The TV issue sheet's entry on the real [IssueDetailViewModel]: whose events are a channel, so the entry must collect them
 * once. One answer has to reach the board and the sheet's own status line together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssueSheetEntryTest {
    @get:Rule
    val composeTestRule = createSeerrAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = FakeSeerrServer()
    private var changed = 0
    private var done = 0

    private fun json(body: String) = FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private fun issueJson(status: Int) =
        """{"id":$ISSUE_ID,"issueType":2,"status":$status,"createdAt":"2026-06-01T10:00:00.000Z",
            "createdBy":{"id":8,"displayName":"ana"},"media":{"id":900,"tmdbId":100,"mediaType":"movie"},"comments":[]}"""

    private fun host(): IssueDetailViewModel {
        var status = 1
        seerr.dispatcher = { request ->
            when ("${request.method} ${request.url.encodedPath}") {
                "POST /api/v1/issue/$ISSUE_ID/resolved" -> json("{}").also { status = 2 }
                "DELETE /api/v1/issue/$ISSUE_ID" -> json("{}")
                "GET /api/v1/issue/$ISSUE_ID" -> json(issueJson(status))
                "GET /api/v1/auth/me" -> json("""{"id":7,"displayName":"Scott","permissions":$ADMIN}""")
                "GET /api/v1/status" -> json("""{"version":"3.1.0"}""")
                "GET /api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                "GET /api/v1/movie/100" -> json("""{"title":"Heat","releaseDate":"1995-12-15"}""")
                else -> FakeResponse(code = 404)
            }
        }
        val connection =
            SeerrConnection(
                store =
                    LegacyCredentialStore(
                        PreferenceDataStoreFactory.create { folder.newFile("d.preferences_pb") },
                        PlainCipher,
                        Dispatchers.Unconfined,
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
            )
        runBlocking { connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow() }
        val vm =
            IssueDetailViewModel(
                connection,
                TitleCache(FakeTitleDao()),
                FakeIssueStore(),
                Dispatchers.Default,
                ISSUE_ID,
                RecordingAnalytics(),
                RecordingCrashBreadcrumbs(),
                SavedStateHandle(),
            )
        composeTestRule.setContent {
            BingeTvTheme { TvIssueSheetEntry(issueId = ISSUE_ID, onDone = { done++ }, onIssueChanged = { changed++ }, viewModel = vm) }
        }
        composeTestRule.waitUntil(TIMEOUT_MILLIS) { vm.uiState.value is IssueDetailUiState.Ready }
        return vm
    }

    @Test
    fun `one resolve reaches the entry's board callback`() {
        val vm = host()
        vm.toggleStatus()
        composeTestRule.waitUntil(TIMEOUT_MILLIS) { changed == 1 }
        composeTestRule.waitForIdle()
        assertEquals(1, changed)
        assertEquals(0, done)
    }

    @Test
    fun `a delete reaches the board and closes the sheet`() {
        val vm = host()
        vm.deleteIssue()
        composeTestRule.waitUntil(TIMEOUT_MILLIS) { done == 1 }
        assertEquals(1, changed)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
