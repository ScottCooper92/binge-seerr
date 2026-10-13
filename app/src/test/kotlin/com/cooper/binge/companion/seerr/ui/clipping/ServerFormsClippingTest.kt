package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.settings.server.ApiKeyActions
import com.cooper.binge.companion.seerr.ui.settings.server.ApiKeyState
import com.cooper.binge.companion.seerr.ui.settings.server.BlocklistTagsActions
import com.cooper.binge.companion.seerr.ui.settings.server.BlocklistTagsScreen
import com.cooper.binge.companion.seerr.ui.settings.server.BlocklistTagsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.KeywordSearch
import com.cooper.binge.companion.seerr.ui.settings.server.LibraryScan
import com.cooper.binge.companion.seerr.ui.settings.server.LibraryType
import com.cooper.binge.companion.seerr.ui.settings.server.MediaLibrary
import com.cooper.binge.companion.seerr.ui.settings.server.MediaServerActions
import com.cooper.binge.companion.seerr.ui.settings.server.MediaServerExtras
import com.cooper.binge.companion.seerr.ui.settings.server.MediaServerForm
import com.cooper.binge.companion.seerr.ui.settings.server.MediaServerKind
import com.cooper.binge.companion.seerr.ui.settings.server.MediaServerScreen
import com.cooper.binge.companion.seerr.ui.settings.server.MetadataExtras
import com.cooper.binge.companion.seerr.ui.settings.server.MetadataForm
import com.cooper.binge.companion.seerr.ui.settings.server.MetadataProvider
import com.cooper.binge.companion.seerr.ui.settings.server.MetadataScreen
import com.cooper.binge.companion.seerr.ui.settings.server.ProviderCheck
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralExtras
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralScreen
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.server.ServerUsersExtras
import com.cooper.binge.companion.seerr.ui.settings.server.ServerUsersScreen
import com.cooper.binge.companion.seerr.ui.settings.server.ServerUsersSettings
import com.cooper.binge.companion.seerr.ui.settings.server.TautulliForm
import com.cooper.binge.companion.seerr.ui.settings.server.TautulliScreen
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_ADDRESS = "https://a-rather-long-hostname.living-room.home.example.org:5055/requests/with/a/path"

/**
 * The server-settings forms with long values and every optional group present, at font scales 1.3 and 2.0, through the
 * design system's `assertNoTextClipped`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerFormsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the general page at 1_3`() = rule.assertClipFree(FONT_13) { GeneralPage() }

    @Test fun `the general page at 2_0`() = rule.assertClipFree(FONT_20) { GeneralPage() }

    @Test fun `the media server page at 1_3`() = rule.assertClipFree(FONT_13) { MediaServerPage() }

    @Test fun `the media server page at 2_0`() = rule.assertClipFree(FONT_20) { MediaServerPage() }

    @Test fun `the server users page at 1_3`() = rule.assertClipFree(FONT_13) { ServerUsersPage() }

    @Test fun `the server users page at 2_0`() = rule.assertClipFree(FONT_20) { ServerUsersPage() }

    @Test fun `the metadata page at 1_3`() = rule.assertClipFree(FONT_13) { MetadataPage() }

    @Test fun `the metadata page at 2_0`() = rule.assertClipFree(FONT_20) { MetadataPage() }

    @Test fun `the tautulli page at 1_3`() = rule.assertClipFree(FONT_13) { TautulliPage() }

    @Test fun `the tautulli page at 2_0`() = rule.assertClipFree(FONT_20) { TautulliPage() }

    @Test fun `the blocklist tags page at 1_3`() = rule.assertClipFree(FONT_13) { BlocklistTagsPage() }

    @Test fun `the blocklist tags page at 2_0`() = rule.assertClipFree(FONT_20) { BlocklistTagsPage() }

    private fun <T> actions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

    @Composable
    private fun GeneralPage() {
        val settings =
            ServerGeneralSettings(
                applicationTitle = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr",
                applicationUrl = LONG_ADDRESS,
                locale = "en",
                discoverRegion = "GB",
                streamingRegion = "GB",
                originalLanguage = "ja|ko",
                hideAvailable = true,
                hideRequested = true,
                partialRequests = true,
                specialEpisodes = true,
                versionCheck = true,
                cacheImages = true,
                youtubeUrl = LONG_ADDRESS,
            )
        ServerGeneralScreen(
            state =
                ExtrasEditorUiState.Ready(
                    draft = settings,
                    saved = settings,
                    extras =
                        ServerGeneralExtras(
                            apiKey = ApiKeyState(key = "MTc1NzQ2MDk5MzEyNA1234abcd".repeat(3)),
                            variant = SeerrVariant.Seerr,
                        ),
                ),
            events = emptyFlow(),
            actions = actions(),
            keyActions = ApiKeyActions(onToggleReveal = {}, onCopy = {}, onRegenerate = {}),
        )
    }

    @Composable
    private fun MediaServerPage() {
        val form =
            MediaServerForm(
                kind = MediaServerKind.Jellyfin,
                serverName = "Alexandria's Attic Jellyfin Server",
                host = "a-rather-long-hostname.attic.home.example.org",
                port = "8096",
                urlBase = "/a/rather/long/url/base",
                forgotPasswordUrl = LONG_ADDRESS,
                apiKey = "0123456789abcdef0123456789abcdef",
            )
        MediaServerScreen(
            state =
                ExtrasEditorUiState.Ready(
                    draft = form,
                    saved = form,
                    extras =
                        MediaServerExtras(
                            libraries =
                                listOf(
                                    MediaLibrary(
                                        "1",
                                        "Movies and Feature-Length Documentaries from Every Decade",
                                        true,
                                        LibraryType.Movies,
                                        null,
                                    ),
                                    MediaLibrary("2", "TV Shows, Miniseries and Limited Anthology Series", true, LibraryType.Shows, null),
                                    MediaLibrary("3", "Home videos", false, null, null),
                                ),
                            scan =
                                LibraryScan(
                                    running = true,
                                    progress = 40,
                                    total = 120,
                                    currentLibrary = "TV Shows, Miniseries and Limited Anthology Series",
                                ),
                        ),
                ),
            events = emptyFlow(),
            actions = actions(),
            serverActions =
                MediaServerActions(
                    onSetLibraryEnabled = { _, _ -> },
                    onSyncLibraries = {},
                    onStartScan = {},
                    onCancelScan = {},
                    onOpenServerPicker = {},
                    onCloseServerPicker = {},
                    onChooseConnection = { _, _ -> },
                    onOpenTautulli = {},
                ),
        )
    }

    @Composable
    private fun ServerUsersPage() {
        val settings =
            ServerUsersSettings(localLogin = true, mediaServerLogin = true, newMediaServerLogin = true, movieLimit = 5_000, movieDays = 365)
        ServerUsersScreen(
            state =
                ExtrasEditorUiState.Ready(
                    draft = settings,
                    saved = settings,
                    extras =
                        ServerUsersExtras(
                            mediaServer = SeerrMediaServer.Jellyfin,
                            defaultPermissions = ManageablePermission.entries.toSet(),
                        ),
                ),
            events = emptyFlow(),
            actions = actions(),
            onOpenDefaultPermissions = {},
        )
    }

    @Composable
    private fun MetadataPage() {
        val form = MetadataForm(tv = MetadataProvider.Tvdb)
        MetadataScreen(
            state =
                ExtrasEditorUiState.Ready(
                    draft = form,
                    saved = form,
                    extras = MetadataExtras(tmdb = ProviderCheck.Operational, tvdb = ProviderCheck.Failed),
                ),
            events = emptyFlow(),
            actions = actions(),
            onTest = {},
        )
    }

    @Composable
    private fun TautulliPage() {
        val form =
            TautulliForm(
                host = "a-rather-long-hostname.tautulli.home.example.org",
                port = "8181",
                useSsl = true,
                urlBase = "/a/rather/long/url/base/for/tautulli",
                apiKey = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
            )
        TautulliScreen(state = EditorUiState.Ready(draft = form, saved = form), events = emptyFlow(), actions = actions())
    }

    @Composable
    private fun BlocklistTagsPage() =
        BlocklistTagsScreen(
            state =
                BlocklistTagsUiState.Ready(
                    tags = listOf(9951, 210024, 4344, 818, 6075),
                    search =
                        KeywordSearch(
                            names =
                                mapOf(
                                    9951 to "kaiju monsters attacking coastal cities in the near future",
                                    210024 to "anime adaptation of a long-running serialised manga",
                                    4344 to "musical",
                                    818 to "based on novel or book",
                                    6075 to "sport",
                                ),
                        ),
                ),
            actions = BlocklistTagsActions(onBack = {}, onSearch = {}, onToggle = {}, onRetry = {}, onReload = {}),
        )
}
