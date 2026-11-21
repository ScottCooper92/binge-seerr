package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.demo.isDemoOrigin
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.seerr.isOpenableSite
import com.cooper.binge.companion.seerr.seerr.releaseNotesUrl
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage

/**
 * The Connection group: the server (opens in the browser), who is signed in, the version, the way
 * into About - open to every signed-in user, admin or not - and the way to edit.
 */
@Composable
internal fun connectionRows(
    connection: ConnectionSummary,
    server: ServerSummary,
    onEditConnection: () -> Unit,
    onOpenPage: (ServerSettingsPage) -> Unit,
    showAbout: Boolean = true,
    onViewDemoAs: (Boolean) -> Unit = {},
    onOpenSheetStates: () -> Unit = {},
): List<ListItem> {
    val context = LocalContext.current
    // The demo has no other server to edit into its place.
    val demo = connection.baseUrl.isDemoOrigin()
    val discovery = LocalDiscoveryEnabled.current
    val viewAsRes = if (connection.demoViewer) R.string.settings_demo_view_as_viewer else R.string.settings_demo_view_as_admin
    return listOfNotNull(
        ListItem(
            icon = Icons.Filled.Link,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_server),
            detail = connection.baseUrl,
            clickable = isOpenableSite(connection.baseUrl),
            destination = ListItemDestination.External,
            onClick = { context.openInBrowser(connection.baseUrl) },
        ),
        ListItem(
            icon = Icons.Filled.Person,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_signed_in_as),
            detail =
                when (connection.signInKind) {
                    SignInKind.ApiKey -> stringResource(R.string.settings_signed_in_api_key)
                    SignInKind.Session -> connection.userName ?: stringResource(R.string.settings_value_unknown)
                },
            clickable = false,
        ),
        ListItem(
            icon = Icons.Filled.Dns,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_version),
            detail = server.versionDetail(),
            clickable = server.updateAvailable,
            destination = ListItemDestination.External,
            onClick = { context.openInBrowser(server.variant.releaseNotesUrl()) },
        ),
        ListItem(
            icon = Icons.Filled.Public,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_about),
            detail = stringResource(R.string.settings_about_caption),
            onClick = { onOpenPage(ServerSettingsPage.About) },
        ).takeIf { showAbout },
        ListItem(
            icon = Icons.Filled.Edit,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_edit_connection),
            detail = stringResource(R.string.settings_edit_connection_caption),
            onClick = onEditConnection,
        ).takeUnless { demo },
        // Demo-only controls. The states are those of the title page's request sheet, so that row needs discovery on.
        ListItem(
            icon = Icons.Filled.Visibility,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_demo_view_as),
            detail = stringResource(viewAsRes),
            onClick = { onViewDemoAs(!connection.demoViewer) },
        ).takeIf { demo },
        ListItem(
            icon = Icons.AutoMirrored.Filled.List,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_demo_sheet_states),
            detail = stringResource(R.string.settings_demo_sheet_states_caption),
            onClick = onOpenSheetStates,
        ).takeIf { demo && discovery },
    )
}

@Composable
private fun ServerSummary.versionDetail(): String {
    val edition =
        versionLabel?.let { stringResource(R.string.setup_server_edition, variant.displayName, it) }
            ?: stringResource(R.string.setup_server_development, variant.displayName)
    return when {
        commitsBehind > 0 -> stringResource(R.string.settings_version_behind, edition, commitsBehind)
        updateAvailable -> stringResource(R.string.settings_version_update, edition)
        else -> edition
    }
}

internal fun onOffRes(on: Boolean): Int = if (on) R.string.settings_value_on else R.string.settings_value_off
