package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.ui.settings.GeneralSettings
import io.github.scottcooper92.binge.seerr.ui.settings.ServerService
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.settings.SystemInfo
import io.github.scottcooper92.binge.seerr.ui.settings.onOffRes
import java.util.Locale

/*
 * What the TV settings board reads out of the server's settings. The board is read-only, every change is made on the
 * phone, so these rows hold values and nothing to tap; the phone's Settings list has its own rows, one per section.
 */

/** The general settings worth seeing from the sofa: the address, the display language and whether Discover hides what is in. */
@Composable
internal fun generalRows(general: GeneralSettings): List<ListItem> =
    listOfNotNull(
        general.applicationUrl?.takeIf { it.isWebUrl() }?.let { url ->
            readOut(Icons.Filled.Link, stringResource(R.string.settings_application_url), url)
        },
        readOut(
            Icons.Filled.Language,
            stringResource(R.string.settings_display_language),
            general.displayLanguage?.let { displayLanguageName(it) } ?: stringResource(R.string.settings_value_unknown),
        ),
        general.hideAvailable?.let { hidden ->
            readOut(Icons.Filled.VisibilityOff, stringResource(R.string.settings_hide_available), stringResource(onOffRes(hidden)))
        },
    )

/** Each Radarr and Sonarr instance, with its markers and where it sends a request. */
@Composable
internal fun serviceRows(services: List<ServerService>): List<ListItem> =
    services.map { service ->
        readOut(if (service.type == ServiceType.Radarr) Icons.Filled.Movie else Icons.Filled.Tv, service.label(), service.detail())
    }

/** Every scheduled job with its next run; a running job says so. */
@Composable
internal fun systemRows(system: SystemInfo): List<ListItem> =
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

@Composable
private fun readOut(
    icon: ImageVector,
    label: String,
    detail: String,
) = ListItem(icon = icon, iconTint = BingeSentiment.Info.fill(), label = label, detail = detail, clickable = false)

@Composable
private fun ServerService.label(): String {
    val markers =
        listOfNotNull(
            if (is4k) stringResource(R.string.settings_service_4k) else null,
            if (isDefault) stringResource(R.string.settings_service_default) else null,
        )
    return (listOf(name) + markers).joinToString(stringResource(R.string.hub_meta_separator))
}

@Composable
private fun ServerService.detail(): String =
    listOfNotNull(qualityProfile, rootFolder).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.hub_meta_separator))
        ?: url?.takeIf { it.isWebUrl() }
        ?: stringResource(R.string.settings_value_unknown)

/** The locale tag's own name in the device's language; the raw tag when the JVM cannot resolve it. */
private fun displayLanguageName(tag: String): String =
    Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()).takeIf { it.isNotBlank() } ?: tag
