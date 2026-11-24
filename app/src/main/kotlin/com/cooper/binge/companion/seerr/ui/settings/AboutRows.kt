package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.runtime.Composable
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.homelab.core.uiandroid.appsettings.aboutRows

private const val PRIVACY_POLICY_URL = "https://scottcooper92.github.io/app-feedback/seerr/privacy/"

/**
 * The About group, which is the shared library's: version, privacy policy, licences, Diagnostics and the beta opt-in.
 * The server's version is in Connection.
 */
@Composable
internal fun aboutGroupRows(
    onOpenLicences: () -> Unit,
    onOpenDiagnostics: () -> Unit,
): List<ListItem> =
    aboutRows(
        versionName = BuildConfig.VERSION_NAME,
        privacyPolicyUrl = PRIVACY_POLICY_URL,
        onOpenLicences = onOpenLicences,
        onOpenDiagnostics = onOpenDiagnostics,
    )
