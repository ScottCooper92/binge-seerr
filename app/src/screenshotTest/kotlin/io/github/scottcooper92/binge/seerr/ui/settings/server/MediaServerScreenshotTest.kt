package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import kotlinx.coroutines.flow.emptyFlow

/**
 * The media-server settings page: the connection form, the libraries with their switches, and the full
 * scan. Plex and Jellyfin draw different forms, so each has a frame. The libraries carry no last-scan
 * time, since that renders against the clock. The Plex server picker is a modal window and does not
 * capture.
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

    /**
     * A full scan on its second library, with a cancel where the start button was. A running scan holds its
     * section open. The scan section sits below the libraries, so this frame leaves the libraries out to keep it in view.
     */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scanRunning() =
        Frame(
            ready(
                plexForm(),
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

    /** A form with no host: Host is marked required, and says so once Save is tried. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unsavedInvalid() =
        Frame(
            ExtrasEditorUiState.Ready(
                draft = plexForm().copy(host = ""),
                saved = plexForm(),
                extras = MediaServerExtras(libraries = libraries()),
            ),
        )

    /** A Jellyfin external host with no scheme: Links for users opens itself and the field says what it wants. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun badExternalUrl() =
        Frame(
            ExtrasEditorUiState.Ready(
                draft = jellyfinForm().copy(externalUrl = "jellyfin.example.com"),
                saved = jellyfinForm(),
                extras = MediaServerExtras(libraries = libraries()),
            ),
        )

    /** A Jellyfin forgot-password link with a trailing slash: only that field is flagged, and its section opens itself. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun badForgotPasswordUrl() =
        Frame(
            ExtrasEditorUiState.Ready(
                draft = jellyfinForm().copy(forgotPasswordUrl = "https://jellyfin.example.com/forgot/"),
                saved = jellyfinForm(),
                extras = MediaServerExtras(libraries = libraries()),
            ),
        )

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
