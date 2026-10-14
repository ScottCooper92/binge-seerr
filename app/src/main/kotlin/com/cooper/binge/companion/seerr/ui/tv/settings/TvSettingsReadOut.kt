package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.ServerService
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.SystemInfo

/*
 * What the TV settings sheet reads out of the server's settings: values with nothing to press, since every change to them
 * is made on the phone. The phone's Settings list has its own rows, one per section.
 */

/** Each Radarr and Sonarr instance by name and its markers; where it files a request is the phone's to show. */
@Composable
internal fun serviceRows(services: List<ServerService>): List<ListItem> =
    services.map { service ->
        ListItem(
            icon = if (service.type == ServiceType.Radarr) Icons.Filled.Movie else Icons.Filled.Tv,
            iconTint = BingeSentiment.Info.fill(),
            label = service.label(),
            clickable = false,
        )
    }

/** Every scheduled job with its next run, worded against [now]; a running job says so. */
@Composable
internal fun systemRows(
    system: SystemInfo,
    now: Long,
): List<ListItem> =
    system.jobs.map { job ->
        ListItem(
            icon = Icons.Filled.Cached,
            iconTint = BingeSentiment.Neutral.fill(),
            label = job.name,
            detail = jobStatusText(job.running, job.nextRunMillis, now),
            clickable = false,
        )
    }

/** A job's line: that it is running, else its next run worded against [now], else unknown. */
@Composable
internal fun jobStatusText(
    running: Boolean,
    nextRunMillis: Long?,
    now: Long,
): String =
    when {
        running -> stringResource(R.string.settings_job_running)
        else ->
            formatRelativeOrAbsolute(nextRunMillis, now)?.let { stringResource(R.string.settings_job_next_run, it) }
                ?: stringResource(R.string.settings_value_unknown)
    }

@Composable
private fun ServerService.label(): String {
    val markers =
        listOfNotNull(
            if (is4k) stringResource(R.string.settings_service_4k) else null,
            if (isDefault) stringResource(R.string.settings_service_default) else null,
        )
    return (listOf(name) + markers).joinToString(stringResource(R.string.hub_meta_separator))
}
