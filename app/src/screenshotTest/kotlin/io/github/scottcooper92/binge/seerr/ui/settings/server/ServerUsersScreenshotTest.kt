package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import kotlinx.coroutines.flow.emptyFlow

/** Settings › Users: how people sign in, everyone's request limits, and the default permissions. */
class ServerUsersScreenshotTest {
    /** Jellyfin on the Jellyseerr lineage: all three sign-in switches, a movie limit with its window, series unlimited. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun jellyseerrLayout() = UsersFrame(ready(lineageUsers()))

    /** Overseerr can't turn its Plex sign-in off, so that switch is absent. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun overseerr() = UsersFrame(ready(lineageUsers().copy(mediaServerLogin = null, movieLimit = 0), SeerrMediaServer.Plex))

    /** Every way in turned off: the group says so, and Save stays off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun noWayIn() = UsersFrame(ready(lineageUsers(), draft = lineageUsers().copy(localLogin = false, mediaServerLogin = false)))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = UsersFrame(ready(lineageUsers()))

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = UsersFrame(ready(lineageUsers()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = UsersFrame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = UsersFrame(ExtrasEditorUiState.Error(SeerrError.Unreachable))
}

private fun lineageUsers() =
    ServerUsersSettings(localLogin = true, mediaServerLogin = true, newMediaServerLogin = false, movieLimit = 5, movieDays = 14)

private fun ready(
    saved: ServerUsersSettings,
    mediaServer: SeerrMediaServer = SeerrMediaServer.Jellyfin,
    draft: ServerUsersSettings = saved,
) = ExtrasEditorUiState.Ready(
    draft = draft,
    saved = saved,
    extras =
        ServerUsersExtras(
            mediaServer = mediaServer,
            defaultPermissions = setOf(ManageablePermission.Request, ManageablePermission.AutoApprove),
        ),
)

@Composable
private fun UsersFrame(state: ExtrasEditorUiState<ServerUsersSettings, ServerUsersExtras>) =
    ServerUsersScreen(
        state = state,
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
        onOpenDefaultPermissions = {},
    )
