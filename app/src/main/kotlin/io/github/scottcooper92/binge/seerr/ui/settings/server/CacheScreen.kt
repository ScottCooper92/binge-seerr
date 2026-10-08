package io.github.scottcooper92.binge.seerr.ui.settings.server

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.state.titleRes
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

class CacheActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryJobs: () -> Unit,
    val onFlush: (String) -> Unit,
    val onFlushDnsEntry: (String) -> Unit,
)

/**
 * The web client's Jobs & Cache page, in its order: the scheduled jobs, each with run, cancel and its schedule; the API
 * caches with a flush each; the DNS cache with its entries where the server has one; and the image caches by size.
 * [jobs] is read on its own, so a jobs list that fails leaves the caches standing, and the other way round: each half
 * shows its own loading and its own failure, with its own retry.
 */
@Composable
fun CacheScreen(
    state: CacheUiState,
    events: Flow<EditorEvent>,
    actions: CacheActions,
    jobs: JobsUiState,
    jobActions: JobsActions,
) {
    ServerActionPage(title = stringResource(R.string.server_settings_cache), events = events, onBack = actions.onBack) { contentPadding ->
        val inset = resolvedContentInset()
        var scheduling by rememberSaveable { mutableStateOf<String?>(null) }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
            JobsSection(jobs, jobActions, inset, actions.onRetryJobs) { scheduling = it }
            when (state) {
                CacheUiState.Loading ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_l)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                is CacheUiState.Error -> {
                    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
                    SectionError(
                        error = state.error,
                        title = stringResource(R.string.server_settings_api_caches),
                        inset = inset,
                        onRetry = actions.onRetry,
                    )
                }
                is CacheUiState.Ready -> CacheSections(state, actions, inset)
            }
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        }
        (jobs as? JobsUiState.Ready)?.jobs?.firstOrNull { it.id == scheduling }?.let { job ->
            ScheduleDialog(
                job = job,
                onConfirm = { cron ->
                    scheduling = null
                    jobActions.onSchedule(job.id, cron)
                },
                onDismiss = { scheduling = null },
            )
        }
    }
}

/** The jobs group. While it loads, or when the server has none, it is absent; a failure is a row with its retry. */
@Composable
private fun JobsSection(
    jobs: JobsUiState,
    jobActions: JobsActions,
    inset: Dp,
    onRetry: () -> Unit,
    onSchedule: (String) -> Unit,
) {
    when (jobs) {
        JobsUiState.Loading -> Unit
        is JobsUiState.Error -> {
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            SectionError(
                error = jobs.error,
                title = stringResource(R.string.server_settings_jobs),
                inset = inset,
                onRetry = onRetry,
            )
        }
        is JobsUiState.Ready ->
            if (jobs.jobs.isNotEmpty()) {
                Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
                ItemGroup(
                    title = stringResource(R.string.server_settings_jobs),
                    rows =
                        jobs.jobs.map { job ->
                            jobRow(job, busy = job.id in jobs.busyIds, outcome = jobs.outcomes[job.id], jobActions) { onSchedule(job.id) }
                        },
                    modifier = Modifier.padding(horizontal = inset),
                )
            }
    }
}

/** A group holding the one failed read, so the rest of the page stands. */
@Composable
private fun SectionError(
    error: SeerrError,
    title: String,
    inset: Dp,
    onRetry: () -> Unit,
) {
    ItemGroup(
        title = title,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Filled.ErrorOutline,
                    iconTint = BingeSentiment.Negative.fill(),
                    label = stringResource(error.titleRes()),
                    detail = stringResource(error.messageRes()),
                    trailingContent = {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_try_again))
                        }
                    },
                    clickable = false,
                ),
            ),
        modifier = Modifier.padding(horizontal = inset),
    )
}

@Composable
private fun CacheSections(
    state: CacheUiState.Ready,
    actions: CacheActions,
    inset: Dp,
) {
    Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    ItemGroup(
        title = stringResource(R.string.server_settings_api_caches),
        rows = state.apiCaches.map { cache -> apiCacheRow(cache, busy = cache.id in state.busyIds, actions.onFlush) },
        modifier = Modifier.padding(horizontal = inset),
    )
    state.dns?.let { dns ->
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        ItemGroup(
            title = stringResource(R.string.server_settings_dns_cache),
            rows =
                listOf(dnsStatsRow(dns)) +
                    dns.entries.map { entry ->
                        dnsEntryRow(entry, busy = "dns:${entry.hostname}" in state.busyIds, actions.onFlushDnsEntry)
                    },
            modifier = Modifier.padding(horizontal = inset),
        )
    }
    if (state.imageCaches.isNotEmpty()) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        ItemGroup(
            title = stringResource(R.string.server_settings_image_caches),
            rows = state.imageCaches.map { imageCacheRow(it) },
            modifier = Modifier.padding(horizontal = inset),
        )
    }
}

@Composable
private fun apiCacheRow(
    cache: ApiCache,
    busy: Boolean,
    onFlush: (String) -> Unit,
): ListItem =
    ListItem(
        icon = Icons.Filled.Storage,
        iconTint = BingeSentiment.Neutral.fill(),
        label = cache.name,
        detail = stringResource(R.string.server_settings_cache_stats, cache.hits, cache.misses, cache.keys),
        trailingContent = { FlushButton(enabled = !busy) { onFlush(cache.id) } },
        clickable = false,
    )

@Composable
private fun imageCacheRow(cache: ImageCache): ListItem =
    ListItem(
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
private fun dnsStatsRow(dns: DnsCache): ListItem =
    ListItem(
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
): ListItem =
    ListItem(
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
