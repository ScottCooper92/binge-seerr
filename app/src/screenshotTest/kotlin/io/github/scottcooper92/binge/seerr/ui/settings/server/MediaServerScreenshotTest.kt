package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import kotlinx.coroutines.flow.emptyFlow

/**
 * The media-server settings page, in the web client's order for each kind: Plex leads with its settings, Jellyfin
 * with its libraries. The libraries carry no last-scan time, since that renders against the clock. The Plex server
 * picker is a modal window and does not capture, so its body is framed on its own in [PlexServerChoicesScreenshotTest].
 */
class MediaServerScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun plexLayout() = Frame(ready(plexForm(), MediaServerExtras(libraries = libraries())))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun jellyfin() = Frame(ready(jellyfinForm(), MediaServerExtras(libraries = libraries())))

    /** A full scan on its second library: the start row shows progress, a bar runs beneath, and a row stops it. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scanRunning() =
        Frame(
            ready(
                jellyfinForm(),
                MediaServerExtras(scan = LibraryScan(running = true, progress = 40, total = 120, currentLibrary = "TV Shows")),
            ),
        )

    /** The server reports no libraries yet, so the section says so and offers the sync. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun noLibraries() = Frame(ready(plexForm(), MediaServerExtras()))

    /** One library's switch is in flight and the sync is running. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun syncingLibraries() =
        Frame(ready(plexForm(), MediaServerExtras(libraries = libraries(), syncingLibraries = true, busyLibraryIds = setOf("2"))))

    /** Jellyfin with no libraries yet, so its settings group, last in the web client's order, is in view whole. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun jellyfinSettings() = Frame(ready(jellyfinForm(), MediaServerExtras()))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = Frame(ready(jellyfinForm(), MediaServerExtras(libraries = libraries())))

    /** At 1.5x and 2x text the pinned Cancel and Save bar must still fit. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = Frame(ready(jellyfinForm(), MediaServerExtras(libraries = libraries())))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = Frame(ExtrasEditorUiState.Error(SeerrError.Unreachable))
}

/** The Plex picker's body: the admin's servers, each a group of its connections, with the server's reachability test. */
class PlexServerChoicesScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun servers() =
        PlexServerChoices(
            PlexServerPicker.Ready(
                listOf(
                    PlexServerChoice(
                        "Den",
                        listOf(
                            PlexConnection("192.168.1.20", 32400, useSsl = false, local = true, reachable = true),
                            PlexConnection("den.example.com", 32400, useSsl = true, local = false, reachable = false),
                        ),
                    ),
                ),
            ),
            noServerActions(),
        )
}

private fun plexForm() =
    MediaServerForm(
        kind = MediaServerKind.Plex,
        serverName = "Attic Plex",
        host = "plex.lan",
        port = "32400",
        useSsl = true,
        externalUrl = "https://app.plex.tv",
    )

private fun jellyfinForm() =
    MediaServerForm(
        kind = MediaServerKind.Jellyfin,
        serverName = "Attic Jellyfin",
        host = "jellyfin.lan",
        port = "8096",
        urlBase = "/jf",
        forgotPasswordUrl = "https://jellyfin.example.com/forgot",
        apiKey = "0123456789abcdef",
    )

private fun libraries() =
    listOf(
        MediaLibrary(id = "1", name = "Movies", enabled = true, type = LibraryType.Movies, lastScanMillis = null),
        MediaLibrary(id = "2", name = "TV Shows", enabled = true, type = LibraryType.Shows, lastScanMillis = null),
        MediaLibrary(id = "3", name = "Home videos", enabled = false, type = null, lastScanMillis = null),
    )

private fun ready(
    form: MediaServerForm,
    extras: MediaServerExtras,
) = ExtrasEditorUiState.Ready(draft = form, saved = form, extras = extras)

@Composable
private fun Frame(state: ExtrasEditorUiState<MediaServerForm, MediaServerExtras>) =
    MediaServerScreen(
        state = state,
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
        serverActions = noServerActions(),
    )

private fun noServerActions() =
    MediaServerActions(
        onSetLibraryEnabled = { _, _ -> },
        onSyncLibraries = {},
        onStartScan = {},
        onCancelScan = {},
        onOpenServerPicker = {},
        onCloseServerPicker = {},
        onChooseConnection = { _, _ -> },
        onOpenTautulli = {},
    )
