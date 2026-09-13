package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.discordUrl
import io.github.scottcooper92.binge.seerr.seerr.docsUrl
import io.github.scottcooper92.binge.seerr.seerr.githubUrl
import io.github.scottcooper92.binge.seerr.seerr.releaseNotesUrl
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** The about page: the edition and its update state, the totals, the server's own facts, and the fork's support links. */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Scaffold(topBar = { BingeTopBar(title = stringResource(R.string.settings_about), onBack = onBack) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                AboutUiState.Loading -> LoadingScreen()
                is AboutUiState.Error -> ErrorScreen(error = state.error, onRetry = onRetry)
                is AboutUiState.Ready -> AboutContent(state.info, onOpenUrl)
            }
        }
    }
}

@Composable
private fun AboutContent(
    info: AboutInfo,
    onOpenUrl: (String) -> Unit,
) {
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        SettingsGroup(
            title = stringResource(R.string.server_settings_about_version),
            rows = versionRows(info, onOpenUrl),
            modifier = Modifier.padding(horizontal = inset),
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        SettingsGroup(
            title = stringResource(R.string.settings_server),
            rows = serverRows(info),
            modifier = Modifier.padding(horizontal = inset),
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        SettingsGroup(
            title = stringResource(R.string.server_settings_about_support),
            rows = supportRows(info, onOpenUrl),
            modifier = Modifier.padding(horizontal = inset),
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
    }
}

@Composable
private fun versionRows(
    info: AboutInfo,
    onOpenUrl: (String) -> Unit,
): List<SettingsRow> =
    listOfNotNull(
        SettingsRow(
            icon = Icons.Filled.Info,
            iconTint = BingeSentiment.Neutral.fill(),
            label = info.variant.displayName,
            detail =
                info.versionLabel?.let { stringResource(R.string.settings_about_version, it) }
                    ?: stringResource(R.string.setup_server_development, info.variant.displayName),
            clickable = false,
        ),
        SettingsRow(
            icon = Icons.Filled.Public,
            iconTint = if (info.updateAvailable) BingeSentiment.Caution.fill() else BingeSentiment.Positive.fill(),
            label =
                when {
                    info.commitsBehind > 0 -> stringResource(R.string.server_settings_about_behind, info.commitsBehind)
                    info.updateAvailable -> stringResource(R.string.server_settings_about_update)
                    else -> stringResource(R.string.server_settings_about_up_to_date)
                },
            detail = stringResource(R.string.server_settings_about_release_notes),
            onClick = { onOpenUrl(info.variant.releaseNotesUrl()) },
        ),
        info.commitTag?.let { tag ->
            SettingsRow(
                icon = Icons.Filled.Code,
                iconTint = BingeSentiment.Neutral.fill(),
                label = stringResource(R.string.server_settings_about_commit),
                detail = tag,
                clickable = false,
            )
        },
    )

@Composable
private fun serverRows(info: AboutInfo): List<SettingsRow> =
    listOfNotNull(
        info.totalRequests?.let { count ->
            SettingsRow(
                icon = Icons.Filled.RequestPage,
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(R.string.server_settings_about_total_requests),
                detail = count.toString(),
                clickable = false,
            )
        },
        info.totalMediaItems?.let { count ->
            SettingsRow(
                icon = Icons.Filled.Movie,
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(R.string.server_settings_about_total_media),
                detail = count.toString(),
                clickable = false,
            )
        },
        info.timezone?.let { zone ->
            SettingsRow(
                icon = Icons.Filled.Schedule,
                iconTint = BingeSentiment.Neutral.fill(),
                label = stringResource(R.string.server_settings_about_timezone),
                detail = zone,
                clickable = false,
            )
        },
        info.appDataPath?.let { path ->
            SettingsRow(
                icon = if (info.appDataWarning) Icons.Filled.Warning else Icons.Filled.Folder,
                iconTint = if (info.appDataWarning) BingeSentiment.Negative.fill() else BingeSentiment.Neutral.fill(),
                label = stringResource(R.string.server_settings_about_data_directory),
                detail =
                    when {
                        info.appDataMounted == false -> stringResource(R.string.server_settings_about_data_not_mounted, path)
                        info.appDataWritable == false -> stringResource(R.string.server_settings_about_data_not_writable, path)
                        else -> path
                    },
                detailColor = if (info.appDataWarning) BingeSentiment.Negative.fill() else null,
                clickable = false,
            )
        },
    )

@Composable
private fun supportRows(
    info: AboutInfo,
    onOpenUrl: (String) -> Unit,
): List<SettingsRow> =
    listOf(
        SettingsRow(
            icon = Icons.Filled.MenuBook,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_about_docs),
            detail = info.variant.docsUrl(),
            onClick = { onOpenUrl(info.variant.docsUrl()) },
        ),
        SettingsRow(
            icon = Icons.Filled.Forum,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_about_discord),
            detail = info.variant.discordUrl(),
            onClick = { onOpenUrl(info.variant.discordUrl()) },
        ),
        SettingsRow(
            icon = Icons.Filled.Code,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_about_github),
            detail = info.variant.githubUrl(),
            onClick = { onOpenUrl(info.variant.githubUrl()) },
        ),
    )
