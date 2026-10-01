package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import kotlinx.coroutines.flow.emptyFlow

/**
 * Settings › General. A field the server's lineage lacks is absent, so the two lineages are two layouts;
 * the page is taller than a device cell, so the sections below the fold are framed on their own in
 * [ServerGeneralSectionsScreenshotTest].
 */
class ServerGeneralScreenshotTest {
    /** Seerr and Jellyseerr: the split regions and the switches the lineage added. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun jellyseerrLayout() = GeneralFrame(generalReady(jellyseerrSettings()))

    /** Overseerr: one region, and the proxy and CSRF switches. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun overseerr() = GeneralFrame(generalReady(overseerrSettings()))

    /** An application URL that is not a web address is marked and keeps Save off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun invalidUrl() =
        GeneralFrame(generalReady(jellyseerrSettings(), draft = jellyseerrSettings().copy(applicationUrl = "requests.home.lan")))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unsavedEdit() = GeneralFrame(generalReady(jellyseerrSettings(), draft = jellyseerrSettings().copy(applicationTitle = "Requests")))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun saving() = GeneralFrame(generalReady(jellyseerrSettings()).copy(saving = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = GeneralFrame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = GeneralFrame(ExtrasEditorUiState.Error(SeerrError.Unreachable))
}

/** The general page's lower sections: the server switches, and the API key. */
class ServerGeneralSectionsScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun serverSwitchesJellyseerr() = SectionFrame { ServerSwitches(jellyseerrSettings(), enabled = true, actions = noActions()) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun serverSwitchesOverseerr() = SectionFrame { ServerSwitches(overseerrSettings(), enabled = true, actions = noActions()) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun apiKeyMasked() = SectionFrame { ApiKeySection(ApiKeyState(key = API_KEY), noKeyActions()) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun apiKeyRevealed() = SectionFrame { ApiKeySection(ApiKeyState(key = API_KEY, revealed = true), noKeyActions()) }

    /** The key is being replaced: its button shows progress, and the old key is already dead. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun apiKeyRegenerating() = SectionFrame { ApiKeySection(ApiKeyState(key = API_KEY, regenerating = true), noKeyActions()) }

    /** No key has been read, so Reveal and Copy are off. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun apiKeyUnread() = SectionFrame { ApiKeySection(ApiKeyState(), noKeyActions()) }
}

private const val API_KEY = "MTc1NzQ2MDk5MzEyNA1234abcd"

private val SECTION_WIDTH = 411.dp
private val SECTION_PADDING = 16.dp
private val SECTION_SPACING = 12.dp

private fun jellyseerrSettings() =
    ServerGeneralSettings(
        applicationTitle = "Seerr",
        applicationUrl = "https://requests.home.lan",
        locale = "en",
        discoverRegion = "GB",
        streamingRegion = "GB",
        originalLanguage = "ja|ko",
        hideAvailable = false,
        hideRequested = true,
        partialRequests = true,
        specialEpisodes = false,
        versionCheck = true,
        cacheImages = true,
        youtubeUrl = "https://www.youtube.com",
    )

private fun overseerrSettings() =
    ServerGeneralSettings(
        applicationTitle = "Overseerr",
        applicationUrl = "https://requests.home.lan",
        locale = "en",
        discoverRegion = "GB",
        originalLanguage = "",
        partialRequests = true,
        versionCheck = true,
        cacheImages = false,
        trustProxy = false,
        csrfProtection = true,
    )

private fun generalReady(
    saved: ServerGeneralSettings,
    draft: ServerGeneralSettings = saved,
) = ExtrasEditorUiState.Ready(draft = draft, saved = saved, extras = ServerGeneralExtras(apiKey = ApiKeyState(key = API_KEY)))

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

private fun noKeyActions() = ApiKeyActions(onToggleReveal = {}, onCopy = {}, onRegenerate = {})

@Composable
private fun SectionFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.width(SECTION_WIDTH).padding(SECTION_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        content = content,
    )
}

@Composable
private fun GeneralFrame(state: ExtrasEditorUiState<ServerGeneralSettings, ServerGeneralExtras>) =
    ServerGeneralScreen(
        state = state,
        events = emptyFlow(),
        actions = noActions(),
        keyActions = noKeyActions(),
        onOpenDefaultPermissions = {},
    )
