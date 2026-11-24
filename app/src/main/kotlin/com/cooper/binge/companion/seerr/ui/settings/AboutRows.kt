package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.material.icons.Icons
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
import com.cooper.binge.companion.seerr.ui.openTestingOptIn
import com.cooper.homelab.core.uiandroid.appsettings.aboutRows

private const val PRIVACY_POLICY_URL = "https://scottcooper92.github.io/app-feedback/seerr/privacy/"

/**
 * The About group: the shared library's version, privacy policy, licences and Diagnostics rows, then this app's own beta opt-in.
 * The server's version is in Connection.
 */
@Composable
internal fun aboutGroupRows(
    onOpenLicences: () -> Unit,
    onOpenDiagnostics: () -> Unit,
): List<ListItem> {
    val context = LocalContext.current
    return aboutRows(
        versionName = BuildConfig.VERSION_NAME,
        privacyPolicyUrl = PRIVACY_POLICY_URL,
        onOpenLicences = onOpenLicences,
        onOpenDiagnostics = onOpenDiagnostics,
    ) +
        ListItem(
            icon = Icons.Filled.Science,
            iconTint = BingeSentiment.Positive.fill(),
            label = stringResource(R.string.about_join_beta),
            destination = ListItemDestination.External,
            onClick = context::openTestingOptIn,
        )
}
