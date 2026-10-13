package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.settings.server.AboutInfo
import com.cooper.binge.companion.seerr.ui.settings.server.AboutScreen
import com.cooper.binge.companion.seerr.ui.settings.server.AboutUiState
import com.cooper.binge.companion.seerr.ui.settings.server.ApiCache
import com.cooper.binge.companion.seerr.ui.settings.server.CacheActions
import com.cooper.binge.companion.seerr.ui.settings.server.CacheScreen
import com.cooper.binge.companion.seerr.ui.settings.server.CacheUiState
import com.cooper.binge.companion.seerr.ui.settings.server.DnsCache
import com.cooper.binge.companion.seerr.ui.settings.server.DnsCacheForm
import com.cooper.binge.companion.seerr.ui.settings.server.DnsEntry
import com.cooper.binge.companion.seerr.ui.settings.server.ImageCache
import com.cooper.binge.companion.seerr.ui.settings.server.JobInterval
import com.cooper.binge.companion.seerr.ui.settings.server.JobsActions
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.NetworkForm
import com.cooper.binge.companion.seerr.ui.settings.server.NetworkScreen
import com.cooper.binge.companion.seerr.ui.settings.server.ProxyForm
import com.cooper.binge.companion.seerr.ui.settings.server.ServerJob
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_HOST = "a-rather-long-hostname.living-room.home.example.org"

/**
 * The server-settings pages with long values and every optional group present, at font scales 1.3 and 2.0, through the
 * design system's `assertNoTextClipped`. The pages that are forms of text fields are in [ServerFormsClippingTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerPagesClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the network page at 1_3`() = rule.assertClipFree(FONT_13) { NetworkPage() }

    @Test fun `the network page at 2_0`() = rule.assertClipFree(FONT_20) { NetworkPage() }

    @Test fun `the about page at 1_3`() = rule.assertClipFree(FONT_13) { AboutPage() }

    @Test fun `the about page at 2_0`() = rule.assertClipFree(FONT_20) { AboutPage() }

    @Test fun `the cache page at 1_3`() = rule.assertClipFree(FONT_13) { CachePage() }

    @Test fun `the cache page at 2_0`() = rule.assertClipFree(FONT_20) { CachePage() }

    @androidx.compose.runtime.Composable
    private fun NetworkPage() {
        val form =
            NetworkForm(
                csrfProtection = true,
                trustProxy = true,
                forceIpv4First = true,
                apiRequestTimeout = "30",
                proxy =
                    ProxyForm(
                        enabled = true,
                        host = LONG_HOST,
                        port = "3128",
                        useSsl = true,
                        user = LONG_NAME,
                        password = "secret",
                        bypassFilter = "*.lan, *.home.example.org, 10.0.0.0/8, 192.168.0.0/16",
                    ),
                dnsCache = DnsCacheForm(enabled = true, minTtl = "5", maxTtl = "60"),
            )
        NetworkScreen(
            state = EditorUiState.Ready(draft = form, saved = form),
            events = emptyFlow(),
            actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
        )
    }

    @androidx.compose.runtime.Composable
    private fun AboutPage() =
        AboutScreen(
            state =
                AboutUiState.Ready(
                    AboutInfo(
                        variant = SeerrVariant.Seerr,
                        versionLabel = "3.1.0-develop.2026.10.12",
                        commitTag = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0",
                        updateAvailable = true,
                        commitsBehind = 1_204,
                        totalRequests = 1_284_000,
                        totalMediaItems = 5_120_000,
                        timezone = "America/Argentina/ComodRivadavia",
                        appDataPath = "/app/config/with/a/rather/long/mounted/path/for/application/data",
                        appDataMounted = false,
                        appDataWritable = false,
                    ),
                ),
            onBack = {},
            onRetry = {},
            onOpenUrl = {},
        )

    @androidx.compose.runtime.Composable
    private fun CachePage() =
        CacheScreen(
            state =
                CacheUiState.Ready(
                    apiCaches =
                        listOf(
                            ApiCache(
                                id = "tmdb",
                                name = "The Movie Database and Its Image Configuration",
                                hits = 1_204_000,
                                misses = 88_000,
                                keys = 240_000,
                            ),
                        ),
                    imageCaches =
                        listOf(
                            ImageCache(name = "tmdb-posters-and-backdrops-and-logos", bytes = 412_000_000_000, imageCount = 1_830_000),
                        ),
                    dns =
                        DnsCache(
                            size = 500,
                            maxSize = 500,
                            hits = 940_000,
                            misses = 22_000,
                            failures = 1_000,
                            entries =
                                listOf(
                                    DnsEntry(
                                        hostname = LONG_HOST,
                                        activeAddress = "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                                        ttlSeconds = 86_400,
                                        hits = 800_000,
                                        misses = 10_000,
                                    ),
                                ),
                        ),
                ),
            events = emptyFlow(),
            actions = CacheActions(onBack = {}, onRetry = {}, onRetryJobs = {}, onFlush = {}, onFlushDnsEntry = {}),
            jobs =
                JobsUiState.Ready(
                    listOf(
                        ServerJob(
                            id = "download-sync",
                            name = "Download sync and orphaned release cleanup for every instance",
                            interval = JobInterval.Short,
                            running = true,
                            nextRunMillis = null,
                        ),
                    ),
                ),
            jobActions = JobsActions(onRun = {}, onCancel = {}, onSchedule = { _, _ -> }),
        )
}
