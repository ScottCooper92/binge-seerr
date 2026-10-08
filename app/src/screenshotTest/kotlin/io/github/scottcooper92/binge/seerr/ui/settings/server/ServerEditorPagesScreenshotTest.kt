package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import kotlinx.coroutines.flow.emptyFlow

/**
 * The server settings pages that edit a draft through the shared editor page: Network, Metadata and
 * Tautulli. Each takes its layout across the device matrix once, then the states the editor page can be
 * in on the phone cell alone: an unchanged draft, one with unsaved edits, a save in flight, one that
 * cannot be saved, and the loading and failed arms.
 */
class NetworkScreenshotTest {
    /** Seerr 3: the switches, the IPv4 preference, the proxy and the DNS cache. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun seerrLayout() = NetworkFrame(settled(seerrNetwork()))

    /** Jellyseerr has only the two switches, and the form sends back no more than the server sent. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun jellyseerr() = NetworkFrame(settled(NetworkForm(csrfProtection = true)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unsavedEdit() = NetworkFrame(EditorUiState.Ready(draft = seerrNetwork().copy(trustProxy = true), saved = seerrNetwork()))

    /** A proxy given a username and no password: the group says what it still needs, and Save stays off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun proxyCredentialsHalf() =
        NetworkFrame(
            EditorUiState.Ready(
                draft = seerrNetwork().copy(dnsCache = DnsCacheForm(), proxy = seerrNetwork().proxy?.copy(user = "seerr")),
                saved = seerrNetwork(),
            ),
        )

    /** A proxy and a DNS cache that are off: each is its switch alone, nothing hanging beneath it. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun proxyAndCacheOff() = NetworkFrame(settled(seerrNetwork().copy(proxy = ProxyForm(), dnsCache = DnsCacheForm())))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = NetworkFrame(settled(seerrNetwork()))

    /** At 1.5x and 2x text the pinned Cancel and Save bar must still fit. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = NetworkFrame(settled(seerrNetwork()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun saving() = NetworkFrame(EditorUiState.Ready(draft = seerrNetwork(), saved = seerrNetwork(), saving = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = NetworkFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = NetworkFrame(EditorUiState.Error(SeerrError.Unreachable))
}

class MetadataScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = MetadataFrame(metadataReady(MetadataForm()))

    /** Series moved to TVDB and not yet saved. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unsavedEdit() = MetadataFrame(metadataReady(MetadataForm(), draft = MetadataForm(tv = MetadataProvider.Tvdb)))

    /** The provider test is running: its button shows progress. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun testing() = MetadataFrame(metadataReady(MetadataForm(), extras = MetadataExtras(testing = true)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = MetadataFrame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = MetadataFrame(ExtrasEditorUiState.Error(SeerrError.Server))
}

class TautulliScreenshotTest {
    /** Nothing configured: the form is empty and cannot be saved. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun emptyLayout() = TautulliFrame(settled(TautulliForm()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun configured() = TautulliFrame(settled(tautulliForm()))

    /** A port out of range marks the field and keeps Save off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun invalidPort() = TautulliFrame(EditorUiState.Ready(draft = tautulliForm().copy(port = "99999"), saved = tautulliForm()))

    /** An external URL with no scheme: the field is flagged and Save stays off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun badExternalUrl() =
        TautulliFrame(EditorUiState.Ready(draft = tautulliForm().copy(externalUrl = "tautulli.example.com"), saved = tautulliForm()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun saving() = TautulliFrame(EditorUiState.Ready(draft = tautulliForm(), saved = tautulliForm(), saving = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = TautulliFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = TautulliFrame(EditorUiState.Error(SeerrError.Unauthorized))
}

private fun <T> settled(form: T) = EditorUiState.Ready(draft = form, saved = form)

private fun seerrNetwork() =
    NetworkForm(
        csrfProtection = false,
        trustProxy = false,
        forceIpv4First = true,
        proxy = ProxyForm(enabled = true, host = "proxy.lan", port = "3128", bypassFilter = "*.lan"),
        dnsCache = DnsCacheForm(enabled = true, minTtl = "5", maxTtl = "60"),
    )

private fun metadataReady(
    saved: MetadataForm,
    draft: MetadataForm = saved,
    extras: MetadataExtras = MetadataExtras(),
) = ExtrasEditorUiState.Ready(draft = draft, saved = saved, extras = extras)

private fun tautulliForm() =
    TautulliForm(host = "tautulli.lan", port = "8181", useSsl = false, urlBase = "/tautulli", apiKey = "a1b2c3d4e5")

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

@Composable
private fun NetworkFrame(state: EditorUiState<NetworkForm>) = NetworkScreen(state = state, events = emptyFlow(), actions = noActions())

@Composable
private fun MetadataFrame(state: ExtrasEditorUiState<MetadataForm, MetadataExtras>) =
    MetadataScreen(state = state, events = emptyFlow(), actions = noActions(), onTest = {})

@Composable
private fun TautulliFrame(state: EditorUiState<TautulliForm>) = TautulliScreen(state = state, events = emptyFlow(), actions = noActions())
