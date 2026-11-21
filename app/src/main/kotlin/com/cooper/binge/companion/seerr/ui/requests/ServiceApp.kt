package com.cooper.binge.companion.seerr.ui.requests

import android.content.Context
import android.content.Intent
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.seerr.isOpenableSite

/** The services a title's sections are about, each of which may have an app of the user's own on the phone. */
internal enum class ServiceKind {
    Radarr,
    Sonarr,
    Jellyfin,
    Emby,
    Plex,
}

/**
 * An app that opens a service: its name for the button, and the packages it ships under, a debug build's after the release one.
 * `<queries>` in the manifests names each package, so an installed one is visible here.
 */
internal data class ServiceApp(
    val name: String,
    val packages: List<String>,
)

/**
 * Which app opens which service. Adding Radarr's or Sonarr's own app is one entry here and one package in `<queries>`.
 */
internal fun serviceApps(debug: Boolean = BuildConfig.DEBUG): Map<ServiceKind, ServiceApp> =
    mapOf(
        ServiceKind.Jellyfin to
            ServiceApp(
                name = "Ephyra",
                packages = listOfNotNull(EPHYRA_PACKAGE, "$EPHYRA_PACKAGE.debug".takeIf { debug }),
            ),
    )

private const val EPHYRA_PACKAGE = "com.cooper.homelab.ephyra"

/** Where a section's Open button goes. */
internal sealed interface OpenTarget {
    /** The user's own app for the service, which is installed as [packageName]. */
    data class App(
        val app: ServiceApp,
        val packageName: String,
    ) : OpenTarget

    /** The service's web page. */
    data class Web(
        val url: String,
    ) : OpenTarget
}

/**
 * Where the section for [this] opens: its app when one is installed, otherwise [webUrl] when it is an address a browser can
 * open, and null when neither, so the section has no button. [isInstalled] is asked of the app's packages in order.
 */
internal fun ServiceKind.openTarget(
    webUrl: String?,
    apps: Map<ServiceKind, ServiceApp> = serviceApps(),
    isInstalled: (String) -> Boolean,
): OpenTarget? {
    val app = apps[this]
    val installed = app?.packages?.firstOrNull(isInstalled)
    return when {
        app != null && installed != null -> OpenTarget.App(app, installed)
        webUrl != null && isOpenableSite(webUrl) -> OpenTarget.Web(webUrl)
        else -> null
    }
}

/**
 * Starts [target]'s app. It opens the app and nothing more for now: opening the title itself needs a contract from the app, and
 * this is the one place that call goes when it exists.
 */
internal fun Context.openServiceApp(target: OpenTarget.App) {
    val launch = packageManager.getLaunchIntentForPackage(target.packageName) ?: return
    attempt { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Whether [packageName] is installed and visible to this app. `getPackageInfo(String, Int)` is the only overload below API 33. */
@Suppress("DEPRECATION")
internal fun Context.isPackageInstalled(packageName: String): Boolean = attempt { packageManager.getPackageInfo(packageName, 0) }.isSuccess
