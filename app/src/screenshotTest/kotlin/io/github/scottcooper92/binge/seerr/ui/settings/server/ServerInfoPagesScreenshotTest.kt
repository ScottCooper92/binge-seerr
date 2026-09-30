package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.coroutines.flow.emptyFlow

/**
 * The server settings pages that only report: About and Cache. Each takes its layout across the
 * device matrix once, then its states and its two whole-screen arms (loading, and a failure with retry)
 * on the phone cell alone.
 */
class AboutScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = AboutFrame(AboutUiState.Ready(aboutInfo()))

    /** The data volume is unmounted or read-only: the warning row. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun dataVolumeWarning() = AboutFrame(AboutUiState.Ready(aboutInfo(appDataMounted = false)))

    /** An Overseerr the user may read only the profile of: no totals, no server facts. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun profileOnly() = AboutFrame(AboutUiState.Ready(profileOnlyInfo()))

    /** A development build has no version and reports how far behind it is. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun developmentBuild() = AboutFrame(AboutUiState.Ready(aboutInfo(versionLabel = null, commitsBehind = 4)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = AboutFrame(AboutUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = AboutFrame(AboutUiState.Error(SeerrError.Unreachable))
}

class CacheScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = CacheFrame(CacheUiState.Ready(apiCaches(), imageCaches(), dnsCache()))

    /** Before Seerr 3 the server has no DNS cache, so the section is absent. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withoutDns() = CacheFrame(CacheUiState.Ready(apiCaches(), imageCaches(), dns = null))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun apiOnly() = CacheFrame(CacheUiState.Ready(apiCaches(), emptyList(), dns = null))

    /** One flush in flight: its row is busy. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun flushing() = CacheFrame(CacheUiState.Ready(apiCaches(), imageCaches(), dnsCache(), busyIds = setOf("tmdb")))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = CacheFrame(CacheUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = CacheFrame(CacheUiState.Error(SeerrError.Server))
}

private fun aboutInfo(
    versionLabel: String? = "3.1.0",
    commitsBehind: Int = 0,
    appDataMounted: Boolean? = true,
) = AboutInfo(
    variant = SeerrVariant.Seerr,
    versionLabel = versionLabel,
    commitTag = null,
    updateAvailable = true,
    commitsBehind = commitsBehind,
    totalRequests = 128,
    totalMediaItems = 512,
    timezone = "Europe/London",
    appDataPath = "/app/config",
    appDataMounted = appDataMounted,
    appDataWritable = true,
)

private fun profileOnlyInfo() =
    AboutInfo(
        variant = SeerrVariant.Overseerr,
        versionLabel = "1.33.2",
        commitTag = null,
        updateAvailable = false,
        commitsBehind = 0,
        totalRequests = null,
        totalMediaItems = null,
        timezone = null,
        appDataPath = null,
        appDataMounted = null,
        appDataWritable = null,
    )

private fun apiCaches() =
    listOf(
        ApiCache(id = "tmdb", name = "The Movie Database", hits = 1204, misses = 88, keys = 240),
        ApiCache(id = "radarr", name = "Radarr API", hits = 310, misses = 12, keys = 41),
    )

private fun imageCaches() =
    listOf(
        ImageCache(name = "tmdb", bytes = 412_000_000, imageCount = 1830),
        ImageCache(name = "avatar", bytes = 3_400_000, imageCount = 27),
    )

private fun dnsCache() =
    DnsCache(
        size = 6,
        maxSize = 500,
        hits = 940,
        misses = 22,
        failures = 1,
        entries =
            listOf(
                DnsEntry(hostname = "api.themoviedb.org", activeAddress = "18.155.68.20", ttlSeconds = 60, hits = 800, misses = 10),
                DnsEntry(hostname = "radarr.lan", activeAddress = null, ttlSeconds = null, hits = 140, misses = 12),
            ),
    )

@Composable
private fun AboutFrame(state: AboutUiState) = AboutScreen(state = state, onBack = {}, onRetry = {}, onOpenUrl = {})

@Composable
private fun CacheFrame(state: CacheUiState) =
    CacheScreen(state = state, events = emptyFlow(), actions = CacheActions(onBack = {}, onRetry = {}, onFlush = {}, onFlushDnsEntry = {}))
