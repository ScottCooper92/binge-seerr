package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.openPlayStoreListing
import com.cooper.binge.companion.seerr.ui.openTestingOptIn

private const val PRIVACY_POLICY_URL = "https://scottcooper92.github.io/binge-seerr/privacy/"

/**
 * The About group: the app's own version (a tap opens its Play listing, which is where a rating is left), the
 * privacy policy, the open-source licences, and the beta opt-in. The server's version is in Connection.
 */
@Composable
internal fun aboutRows(onOpenLicences: () -> Unit): List<ListItem> {
    val context = LocalContext.current
    return listOf(
        ListItem(
            icon = Icons.Filled.Info,
            iconTint = BingeSentiment.Neutral.fill(),
            label = stringResource(R.string.about_app_version),
            detail = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
            destination = ListItemDestination.External,
            onClick = context::openPlayStoreListing,
        ),
        ListItem(
            icon = Icons.AutoMirrored.Filled.Article,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.about_privacy_policy),
            destination = ListItemDestination.External,
            onClick = { context.openInBrowser(PRIVACY_POLICY_URL) },
        ),
        ListItem(
            icon = Icons.Filled.Description,
            iconTint = BingeSentiment.Neutral.fill(),
            label = stringResource(R.string.licences_title),
            onClick = onOpenLicences,
        ),
        ListItem(
            icon = Icons.Filled.Science,
            iconTint = BingeSentiment.Positive.fill(),
            label = stringResource(R.string.about_join_beta),
            destination = ListItemDestination.External,
            onClick = context::openTestingOptIn,
        ),
    )
}
