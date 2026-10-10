package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.preview.WithPreviewAvatarImage
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews

/**
 * The hub bar's account action (#1325) in each of its ring's states, on the bar's own colour: unlimited is a full green
 * ring, and a metered account's ring fills green, then yellow from half used, then red from four fifths.
 */
class HubAccountActionScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun unlimited() = Frame(HubQuota(movie = null, tv = null))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun nothingUsed() = Frame(HubQuota(movie = bucket(remaining = 10), tv = bucket(remaining = 10)))

    /** Six of ten TV requests used: the tighter bucket, past half, so yellow. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun mostlyUsed() = Frame(HubQuota(movie = bucket(remaining = 8), tv = bucket(remaining = 4)))

    /** Nine of ten movie requests used: red. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun nearlySpent() = Frame(HubQuota(movie = bucket(remaining = 1), tv = null))

    /** A photo in place of the initials, inside the same ring. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun withPhoto() = WithPreviewAvatarImage { Frame(HubQuota(movie = bucket(remaining = 7), tv = null), avatarUrl = PHOTO) }

    /** The whole hub in Spanish, the action at the end of the bar beside the server's name. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun hubSpanish() = HubScreen(state = previewReady(), actions = previewActions())
}

private const val PHOTO = "https://seerr.example/avatar/ada.png"

private fun bucket(remaining: Int) = HubQuotaBucket(limit = 10, remaining = remaining, days = 7)

@Composable
private fun Frame(
    quota: HubQuota,
    avatarUrl: String? = null,
) {
    Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
        HubAccountAction(
            account = HubAccount(id = 1, name = "Ada Lovelace", isAdmin = false, avatarUrl = avatarUrl),
            quota = quota,
            onClick = {},
        )
    }
}
