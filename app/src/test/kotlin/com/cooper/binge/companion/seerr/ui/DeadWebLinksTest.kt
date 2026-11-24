package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.issues.IssueManageContent
import com.cooper.binge.companion.seerr.ui.issues.commonIssueDetail
import com.cooper.binge.companion.seerr.ui.issues.hasOpenableLink
import com.cooper.binge.companion.seerr.ui.requests.FilesDeletion
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.RequestOpenLink
import com.cooper.binge.companion.seerr.ui.requests.RequestSheetCallbacks
import com.cooper.binge.companion.seerr.ui.requests.deleteFilesRow
import com.cooper.binge.companion.seerr.ui.requests.rememberRequestOpenLinks
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.settings.connectionRows
import com.cooper.binge.companion.seerr.ui.users.UserActionsContent
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.ui.users.hasManageActions
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.discover.R as DiscoverR

/**
 * A link to the server's own page that cannot be a real site (the demo's address is under `.invalid`) is not offered:
 * a row that opens nothing is worse than none. The decision is on the address, so a real server keeps every row. One test
 * pair per call site.
 */
@RunWith(RobolectricTestRunner::class)
class DeadWebLinksTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private val demo = "https://demo.seerr.invalid/"
    private val real = "https://seerr.example.com/"

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { SeerrTheme { content() } }
        compose.waitForIdle()
    }

    private fun isShown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun user(webUrl: String) =
        UserDetail(
            item =
                UserItem(
                    id = 7,
                    name = "Ada",
                    email = null,
                    handle = null,
                    avatarUrl = null,
                    origin = UserOrigin.Plex,
                    permissions = 0,
                    requestCount = 0,
                    createdAtMillis = null,
                ),
            permissions = emptySet(),
            quota = null,
            watch = null,
            watchlist = emptyList(),
            isSelf = false,
            canEditSettings = false,
            canDelete = false,
            serverUrl = real,
            webUrl = webUrl,
        )

    @Test
    fun `a user's Manage sheet offers the server page on a real server`() {
        val detail = user(real + "users/7")
        show { UserActionsContent(detail, deleting = false, onOpenSettings = {}, onOpenWeb = {}, onDelete = {}) }
        assertTrue(isShown(string(DiscoverR.string.open_in_named, detail.serverName)))
    }

    @Test
    fun `a user's Manage sheet leaves out the server page when its address cannot be opened`() {
        val detail = user(demo + "users/7")
        show { UserActionsContent(detail, deleting = false, onOpenSettings = {}, onOpenWeb = {}, onDelete = {}) }
        assertFalse(isShown(string(DiscoverR.string.open_in_named, detail.serverName)))
    }

    private fun issueSheet(
        web: String,
        mediaServer: String?,
        service: String?,
    ) = show {
        IssueManageContent("Seerr", "Plex", "Radarr", web, {}, mediaServerUrl = mediaServer, serviceUrl = service)
    }

    @Test
    fun `an issue's Manage sheet offers every link on a real server`() {
        issueSheet(real + "issues/4", "https://plex.example.com/", "https://radarr.example.com/")
        listOf("Seerr", "Plex", "Radarr").forEach { assertTrue(it, isShown(string(DiscoverR.string.open_in_named, it))) }
    }

    @Test
    fun `an issue's Manage sheet leaves out each link whose address cannot be opened`() {
        issueSheet(demo + "issues/4", "https://plex.example.com/", demo)
        assertFalse("the server page", isShown(string(DiscoverR.string.open_in_named, "Seerr")))
        assertTrue("a real media server stays", isShown(string(DiscoverR.string.open_in_named, "Plex")))
        assertFalse("the service", isShown(string(DiscoverR.string.open_in_named, "Radarr")))
    }

    private fun requestLinks(
        web: String,
        mediaServer: String?,
        service: String?,
    ): List<RequestOpenLink> {
        var links = emptyList<RequestOpenLink>()
        val detail = requestDetail().copy(webUrl = web, mediaServerUrl = mediaServer, serviceUrl = service)
        show { links = rememberRequestOpenLinks(detail) }
        return links
    }

    @Test
    fun `a request's open sheet offers the title, the media server and the service on a real server`() {
        assertEquals(3, requestLinks(real + "movie/1", "https://plex.example.com/", "https://radarr.example.com/").size)
    }

    @Test
    fun `a request's open sheet has no links at all when every address cannot be opened`() {
        assertTrue(requestLinks(demo + "movie/1", demo, demo).isEmpty())
    }

    @Test
    fun `a request's open sheet keeps a real link beside one that cannot be opened`() {
        assertEquals(1, requestLinks(demo + "movie/1", "https://plex.example.com/", null).size)
    }

    private fun serverRow(baseUrl: String): ListItem {
        var rows = emptyList<ListItem>()
        val server = ServerSummary("Seerr", SeerrVariant.Seerr, "3.0", updateAvailable = false, commitsBehind = 0)
        show { rows = connectionRows(ConnectionSummary(baseUrl, SignInKind.ApiKey, "Ada"), server, {}, {}) }
        return rows.first()
    }

    @Test
    fun `the settings server row opens a real server's address`() {
        assertTrue(serverRow(real).clickable)
    }

    @Test
    fun `the settings server row still shows an address that cannot be opened, and does not open it`() {
        val row = serverRow(demo)
        assertEquals(demo, row.detail)
        assertFalse(row.clickable)
    }

    private fun filesRow(
        servicesUrl: String?,
        inApp: Boolean,
    ): ListItem {
        var row: ListItem? = null
        val instance =
            MediaInstance(
                is4k = false,
                status = SeerrMediaStatusCode.Available,
                serviceUrl = null,
                mediaServerUrl = null,
                watch = null,
                filesDeletion = FilesDeletion.NoDefaultServer,
            )
        val media =
            MediaRecord(
                mediaId = 9,
                isTv = false,
                instances = listOf(instance),
                canSetStatus = false,
                canClearData = false,
                canDeleteFiles = true,
                servicesSettingsUrl = servicesUrl,
            )
        val callbacks = RequestSheetCallbacks({}, {}, {}, {}, onOpenServices = if (inApp) ({}) else null)
        show { row = deleteFilesRow(instance, media, "Radarr", callbacks) }
        return checkNotNull(row)
    }

    @Test
    fun `the no default server row opens the web client's settings on a real server`() {
        assertTrue(filesRow(real + "settings/services", inApp = false).clickable)
    }

    @Test
    fun `the no default server row does not open an address that cannot be opened`() {
        assertFalse(filesRow(demo + "settings/services", inApp = false).clickable)
    }

    @Test
    fun `the no default server row still opens this app's own settings where the screen can reach them`() {
        assertTrue(filesRow(demo + "settings/services", inApp = true).clickable)
    }

    private fun issue(
        web: String,
        mediaServer: String? = null,
        service: String? = null,
    ) = commonIssueDetail().copy(webUrl = web, mediaServerUrl = mediaServer, serviceUrl = service)

    @Test
    fun `an issue page has an actions button on a real server`() {
        assertTrue(issue(real + "issues/4").hasOpenableLink())
    }

    @Test
    fun `an issue page has no actions button when every address cannot be opened`() {
        assertFalse(issue(demo + "issues/4", demo, demo).hasOpenableLink())
    }

    @Test
    fun `an issue page keeps its actions button for one link that can be opened`() {
        assertTrue(issue(demo + "issues/4", mediaServer = "https://plex.example.com/").hasOpenableLink())
    }

    @Test
    fun `a user page has no actions button when it would hold no row`() {
        assertFalse(user(demo + "users/7").hasManageActions())
    }

    @Test
    fun `a user page keeps its actions button for any one row`() {
        assertTrue(user(real + "users/7").hasManageActions())
        assertTrue(user(demo + "users/7").copy(canEditSettings = true).hasManageActions())
        assertTrue(user(demo + "users/7").copy(canDelete = true).hasManageActions())
    }
}
