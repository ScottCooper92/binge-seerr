package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SEERR_CACHE =
    """{"apiCaches":[{"id":"tmdb","name":"The Movie Database","stats":{"hits":120,"misses":8,"keys":40,"ksize":1,"vsize":2}},
                     {"id":"radarr","name":"Radarr","stats":{"hits":3,"misses":1,"keys":2}}],
        "imageCache":{"tmdb":{"size":1048576,"imageCount":12},"avatar":{"size":2048,"imageCount":1}},
        "dnsCache":{"stats":{"size":2,"maxSize":500,"hits":19,"misses":1,"failures":0,"ipv4Fallbacks":0,"hitRate":0.95},
                    "entries":{"api.themoviedb.org":{"addresses":{"ipv4":1,"ipv6":0},"activeAddress":"1.2.3.4","family":4,"age":10,"ttl":300,"hits":19,"misses":1},
                               "image.tmdb.org":{"activeAddress":"5.6.7.8","family":4,"ttl":60,"hits":0,"misses":0}}}}"""

private const val OVERSEERR_CACHE =
    """{"apiCaches":[{"id":"tmdb","name":"The Movie Database","stats":{"hits":1,"misses":0,"keys":1,"ksize":0,"vsize":0}}],
        "imageCache":{"tmdb":{"size":100,"imageCount":1}}}"""

class CacheViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): CacheViewModel {
        val vm = CacheViewModel(seerr.connection(this))
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun CacheViewModel.awaitReady(): CacheUiState.Ready = uiState.first { it is CacheUiState.Ready } as CacheUiState.Ready

    @Test
    fun `a seerr's caches are read with their counts, sizes and dns entries, and a flush re-reads them`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/settings/cache", SEERR_CACHE)
            seerr.serve("POST /api/v1/settings/cache/tmdb/flush", "", code = 204)
            seerr.serve("POST /api/v1/settings/cache/dns/image.tmdb.org/flush", "", code = 204)
            val vm = viewModel()
            val ready = vm.awaitReady()
            assertEquals(listOf("The Movie Database", "Radarr"), ready.apiCaches.map { it.name })
            assertEquals(120L, ready.apiCaches[0].hits)
            assertEquals(listOf("tmdb" to 1_048_576L, "avatar" to 2_048L), ready.imageCaches.map { it.name to it.bytes })
            assertEquals(listOf("api.themoviedb.org", "image.tmdb.org"), ready.dns?.entries?.map { it.hostname })
            assertEquals(
                300L,
                ready.dns
                    ?.entries
                    ?.first()
                    ?.ttlSeconds,
            )
            assertEquals(19L, ready.dns?.hits)

            vm.flush("tmdb")
            assertEquals(EditorEvent.Notice(R.string.server_settings_cache_flushed), vm.events.first())
            assertEquals(1, seerr.count("POST", "/api/v1/settings/cache/tmdb/flush"))
            assertEquals(2, seerr.count("GET", "/api/v1/settings/cache"))
            assertTrue(vm.awaitReady().busyIds.isEmpty())

            vm.flushDnsEntry("image.tmdb.org")
            assertEquals(EditorEvent.Notice(R.string.server_settings_cache_flushed), vm.events.first())
            assertEquals(1, seerr.count("POST", "/api/v1/settings/cache/dns/image.tmdb.org/flush"))
        }

    @Test
    fun `an overseerr has no dns cache, and a failed flush is reported`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, version = "1.33.2", settings = "{}")
            seerr.serve("GET /api/v1/settings/cache", OVERSEERR_CACHE)
            seerr.serve("POST /api/v1/settings/cache/tmdb/flush", """{"message":"nope"}""", code = 500)
            val vm = viewModel()
            val ready = vm.awaitReady()
            assertNull(ready.dns)
            assertEquals(1, ready.imageCaches.size)

            vm.flush("tmdb")
            assertTrue(vm.events.first() is EditorEvent.Failed)
            assertTrue(vm.awaitReady().busyIds.isEmpty())
        }
}
