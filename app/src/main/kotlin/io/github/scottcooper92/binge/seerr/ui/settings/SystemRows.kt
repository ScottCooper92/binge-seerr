package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage

/** Every scheduled job with its next run — a running job says so — and the caches; read-only for jobs, the caches and logs open their pages. */
@Composable
internal fun systemRows(
    system: SystemInfo,
    onOpenPage: (ServerSettingsPage) -> Unit,
): List<ListItem> = readOnlyJobRows(system) + systemLinkRows(onOpenPage)

@Composable
private fun readOnlyJobRows(system: SystemInfo): List<ListItem> =
    system.jobs.map { job ->
        ListItem(
            icon = Icons.Filled.Cached,
            iconTint = BingeSentiment.Neutral.fill(),
            label = job.name,
            detail =
                when {
                    job.running -> stringResource(R.string.settings_job_running)
                    else ->
                        formatRelativeOrAbsolute(job.nextRunMillis)?.let { stringResource(R.string.settings_job_next_run, it) }
                            ?: stringResource(R.string.settings_value_unknown)
                },
            clickable = false,
        )
    }

/** The caches and the log: the System group's rows that open a page. */
@Composable
internal fun systemLinkRows(onOpenPage: (ServerSettingsPage) -> Unit): List<ListItem> =
    listOf(
        ListItem(
            icon = Icons.Filled.Storage,
            iconTint = BingeSentiment.Neutral.fill(),
            label = stringResource(R.string.server_settings_cache),
            detail = stringResource(R.string.server_settings_cache_caption),
            onClick = { onOpenPage(ServerSettingsPage.Cache) },
        ),
        ListItem(
            icon = Icons.AutoMirrored.Filled.Article,
            iconTint = BingeSentiment.Neutral.fill(),
            label = stringResource(R.string.server_settings_logs),
            detail = stringResource(R.string.server_settings_logs_caption),
            onClick = { onOpenPage(ServerSettingsPage.Logs) },
        ),
    )
