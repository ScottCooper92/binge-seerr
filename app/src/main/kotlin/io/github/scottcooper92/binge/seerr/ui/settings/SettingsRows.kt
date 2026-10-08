package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.seerr.releaseNotesUrl
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage

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
    return listOfNotNull(
        ListItem(
            icon = Icons.Filled.Link,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_server),
            detail = connection.baseUrl,
            clickable = connection.baseUrl.isWebUrl(),
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
        ),
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

@Composable
internal fun requestPolicyRows(policy: RequestPolicy): List<ListItem> =
    listOf(
        ListItem(
            icon = Icons.Filled.Shield,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_default_permissions),
            detail =
                stringResource(
                    when (policy.defaultAccess) {
                        SeerrDefaultAccess.NoRequests -> R.string.settings_default_access_none
                        SeerrDefaultAccess.RequestWithApproval -> R.string.settings_default_access_request
                        SeerrDefaultAccess.AutoApprove -> R.string.settings_default_access_auto
                    },
                ),
            clickable = false,
        ),
        ListItem(
            icon = Icons.Filled.RequestPage,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_request_limit),
            detail = requestLimitDetail(policy.movieLimit, policy.tvLimit),
            clickable = false,
        ),
    )

@Composable
private fun requestLimitDetail(
    movie: RequestLimit?,
    tv: RequestLimit?,
): String {
    if (movie == null && tv == null) return stringResource(R.string.settings_request_limit_unlimited)
    return listOf(
        stringResource(R.string.settings_request_limit_movie, movie.limitText()),
        stringResource(R.string.settings_request_limit_tv, tv.limitText()),
    ).joinToString(stringResource(R.string.hub_meta_separator))
}

@Composable
private fun RequestLimit?.limitText(): String =
    this?.let { stringResource(R.string.settings_request_limit_value, it.count, it.days) }
        ?: stringResource(R.string.settings_request_limit_unlimited)

internal fun onOffRes(on: Boolean): Int = if (on) R.string.settings_value_on else R.string.settings_value_off
