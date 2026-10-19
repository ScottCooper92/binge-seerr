package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.demo.isDemoOrigin
import com.cooper.binge.companion.seerr.seerr.isWebUrl
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
): List<ListItem> {
    val context = LocalContext.current
    // The demo's address is not a page: nothing is there to open, and no other server is edited into its place.
    val demo = connection.baseUrl.isDemoOrigin()
    return listOfNotNull(
        ListItem(
            icon = Icons.Filled.Link,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_server),
            detail = connection.baseUrl,
            clickable = connection.baseUrl.isWebUrl() && !demo,
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
