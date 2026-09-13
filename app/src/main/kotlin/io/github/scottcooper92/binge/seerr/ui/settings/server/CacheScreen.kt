package io.github.scottcooper92.binge.seerr.ui.settings.server

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

class CacheActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onFlush: (String) -> Unit,
    val onFlushDnsEntry: (String) -> Unit,
)

/** The cache page: the API caches with a flush each, the image caches by size, and the DNS cache with its entries where the server has one. */
@Composable
fun CacheScreen(
    state: CacheUiState,
    events: Flow<EditorEvent>,
    actions: CacheActions,
) {
    ServerActionPage(title = stringResource(R.string.server_settings_cache), events = events, onBack = actions.onBack) {
        when (state) {
            CacheUiState.Loading -> LoadingScreen()
            is CacheUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
            is CacheUiState.Ready -> CacheContent(state, actions)
        }
    }
}

@Composable
private fun CacheContent(
    state: CacheUiState.Ready,
    actions: CacheActions,
) {
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        SettingsGroup(
            title = stringResource(R.string.server_settings_api_caches),
            rows = state.apiCaches.map { cache -> apiCacheRow(cache, busy = cache.id in state.busyIds, actions.onFlush) },
            modifier = Modifier.padding(horizontal = inset),
        )
        if (state.imageCaches.isNotEmpty()) {
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            SettingsGroup(
                title = stringResource(R.string.server_settings_image_caches),
                rows = state.imageCaches.map { imageCacheRow(it) },
                modifier = Modifier.padding(horizontal = inset),
            )
        }
        state.dns?.let { dns ->
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            SettingsGroup(
                title = stringResource(R.string.server_settings_dns_cache),
                rows =
                    listOf(dnsStatsRow(dns)) +
                        dns.entries.map { entry ->
                            dnsEntryRow(entry, busy = "dns:${entry.hostname}" in state.busyIds, actions.onFlushDnsEntry)
                        },
                modifier = Modifier.padding(horizontal = inset),
            )
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    }
}

@Composable
private fun apiCacheRow(
    cache: ApiCache,
    busy: Boolean,
    onFlush: (String) -> Unit,
): SettingsRow =
    SettingsRow(
        icon = Icons.Filled.Storage,
        iconTint = BingeSentiment.Neutral.fill(),
        label = cache.name,
        detail = stringResource(R.string.server_settings_cache_stats, cache.hits, cache.misses, cache.keys),
        trailingContent = { FlushButton(enabled = !busy) { onFlush(cache.id) } },
        clickable = false,
    )

@Composable
private fun imageCacheRow(cache: ImageCache): SettingsRow =
    SettingsRow(
        icon = Icons.Filled.Image,
        iconTint = BingeSentiment.Neutral.fill(),
        label = cache.name,
        detail =
            stringResource(
                R.string.server_settings_image_cache_stats,
                Formatter.formatShortFileSize(LocalContext.current, cache.bytes),
                cache.imageCount,
            ),
        clickable = false,
    )

@Composable
private fun dnsStatsRow(dns: DnsCache): SettingsRow =
    SettingsRow(
        icon = Icons.Filled.Dns,
        iconTint = BingeSentiment.Neutral.fill(),
        label = stringResource(R.string.server_settings_dns_entries, dns.size, dns.maxSize),
        detail = stringResource(R.string.server_settings_dns_stats, dns.hits, dns.misses, dns.failures),
        clickable = false,
    )

@Composable
private fun dnsEntryRow(
    entry: DnsEntry,
    busy: Boolean,
    onFlush: (String) -> Unit,
): SettingsRow =
    SettingsRow(
        icon = Icons.Filled.Dns,
        iconTint = BingeSentiment.Info.fill(),
        label = entry.hostname,
        detail =
            listOfNotNull(entry.activeAddress, entry.ttlSeconds?.let { stringResource(R.string.server_settings_dns_ttl, it) })
                .joinToString(stringResource(R.string.hub_meta_separator))
                .ifEmpty { stringResource(R.string.settings_value_unknown) },
        trailingContent = { FlushButton(enabled = !busy) { onFlush(entry.hostname) } },
        clickable = false,
    )

@Composable
private fun FlushButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.server_settings_cache_flush))
    }
}
