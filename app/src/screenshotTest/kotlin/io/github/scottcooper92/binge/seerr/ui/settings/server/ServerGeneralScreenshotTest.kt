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
import io.github.scottcooper92.binge.seerr.preview.PHONE_WIDTH
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import kotlinx.coroutines.flow.emptyFlow

/**
 * Settings › General. A row the server's lineage lacks is absent, so the two lineages are two layouts; the page is
 * taller than a device cell, so the groups below the fold, and the sheets the rows open, are framed in
 * [ServerGeneralPartsScreenshotTest].
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
    fun overseerr() = GeneralFrame(generalReady(overseerrSettings(), SeerrVariant.Overseerr))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = GeneralFrame(generalReady(jellyseerrSettings()))

    /** At 1.5x and 2x text the pinned Cancel and Save bar must still fit. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = GeneralFrame(generalReady(jellyseerrSettings()))

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

/** The general page's groups below the fold, and what its sheets hold. */
class ServerGeneralPartsScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun requestsAndAdvancedJellyseerr() =
        PartFrame {
            RequestsGroup(jellyseerrSettings(), enabled = true, actions = noActions())
            AdvancedGroup(jellyseerrSettings(), enabled = true, actions = noActions())
        }

    /** Overseerr has no special episodes or YouTube address, and keeps the proxy and CSRF switches in Advanced. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun requestsAndAdvancedOverseerr() =
        PartFrame {
            RequestsGroup(overseerrSettings(), enabled = true, actions = noActions())
            AdvancedGroup(overseerrSettings(), enabled = true, actions = noActions())
        }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun apiKeyMasked() = PartFrame { ApiKeyRows(ApiKeyState(key = API_KEY), noKeyActions(), onRegenerate = {}) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun apiKeyRevealed() = PartFrame { ApiKeyRows(ApiKeyState(key = API_KEY, revealed = true), noKeyActions(), onRegenerate = {}) }

    /** The key is being replaced: Regenerate shows progress, and the old key is already dead. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun apiKeyRegenerating() = PartFrame { ApiKeyRows(ApiKeyState(key = API_KEY, regenerating = true), noKeyActions(), onRegenerate = {}) }

    /** A server's automatic blocklist: its own region and languages, two tags named, and the pages taken per tag. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun blocklist() =
        PartFrame {
            BlocklistGroup(
                BlocklistSettings(region = "GB", languages = "ja", tags = "9951,210024", tagsLimit = "50"),
                ServerGeneralExtras(keywords = KeywordSearch(names = mapOf(9951 to "kaiju", 210024 to "anime"))),
                enabled = true,
                onLoadList = {},
                keywordActions = KeywordActions(onLoadNames = {}, onOpen = {}),
                actions = noActions(),
            )
        }
}

private const val API_KEY = "MTc1NzQ2MDk5MzEyNA1234abcd"

private val PART_PADDING = 16.dp
private val PART_SPACING = 12.dp

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
    variant: SeerrVariant = SeerrVariant.Seerr,
    draft: ServerGeneralSettings = saved,
) = ExtrasEditorUiState.Ready(
    draft = draft,
    saved = saved,
    extras = ServerGeneralExtras(apiKey = ApiKeyState(key = API_KEY), variant = variant),
)

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

private fun noKeyActions() = ApiKeyActions(onToggleReveal = {}, onCopy = {}, onRegenerate = {})

@Composable
private fun PartFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.width(PHONE_WIDTH).padding(PART_PADDING),
        verticalArrangement = Arrangement.spacedBy(PART_SPACING),
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
    )
