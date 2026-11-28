package com.cooper.binge.companion.seerr.ui.requests

import android.content.Context
import android.content.Intent
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.core.ui.BuildConfig
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.isOpenableSite

/** The services a title's sections are about, each of which may have an app of the user's own on the phone. */
enum class ServiceKind {
    Radarr,
    Sonarr,
    Jellyfin,
    Emby,
    Plex,
    ;

    /** Whether this is the server the titles are on rather than a download manager. */
    val isMediaServer: Boolean get() = this == Jellyfin || this == Emby || this == Plex
}

/** The service kind of the media server Seerr is set up with, where there is one. */
fun SeerrMediaServer.toServiceKind(): ServiceKind? =
    when (this) {
        SeerrMediaServer.Jellyfin -> ServiceKind.Jellyfin
        SeerrMediaServer.Emby -> ServiceKind.Emby
        SeerrMediaServer.Plex -> ServiceKind.Plex
        SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> null
    }

/**
 * What an app is told about the title, all of it optional. [serverUrl] is the media server's base address, so an app opens the item
 * only when it is signed in to that server; [itemId] is the title's id on it, and [tmdbType] and [tmdbId] are the fallback.
 */
data class ServiceItem(
    val serverUrl: String? = null,
    val itemId: String? = null,
    val tmdbType: String? = null,
    val tmdbId: Int? = null,
)

/**
 * An app that opens a service: its name for the button, and the intents that open [ServiceItem] in it, in the order to try them.
 * The manifests' `<queries>` name each intent's action, so an installed app that answers one is visible here.
 */
data class ServiceApp(
    val name: String,
    val intentsFor: (ServiceItem) -> List<Intent>,
)

/**
 * Which app opens which service. Adding Radarr's or Sonarr's own app is one entry here and the action it answers in `<queries>`.
 */
fun serviceApps(debug: Boolean = BuildConfig.DEBUG): Map<ServiceKind, ServiceApp> =
    mapOf(
        ServiceKind.Jellyfin to
            ServiceApp(
                name = "Ephyra",
                intentsFor = { item ->
                    listOfNotNull(EPHYRA_PACKAGE, "$EPHYRA_PACKAGE.debug".takeIf { debug }).map { ephyraOpenItem(it, item) }
                },
            ),
    )

private const val EPHYRA_PACKAGE = "com.cooper.homelab.ephyra"

/** Ephyra's open-item contract: the action, the package it answers in and the four optional extras. */
const val EPHYRA_ACTION_OPEN_ITEM = "com.cooper.homelab.ephyra.action.OPEN_ITEM"
const val EPHYRA_EXTRA_SERVER_URL = "com.cooper.homelab.ephyra.extra.SERVER_URL"
const val EPHYRA_EXTRA_ITEM_ID = "com.cooper.homelab.ephyra.extra.ITEM_ID"
const val EPHYRA_EXTRA_TMDB_TYPE = "com.cooper.homelab.ephyra.extra.TMDB_TYPE"
const val EPHYRA_EXTRA_TMDB_ID = "com.cooper.homelab.ephyra.extra.TMDB_ID"

/**
 * The intent that opens [item] in Ephyra. It sends every extra it has: Ephyra uses the item id only when the server address matches
 * the server it is signed in to, then falls back to the TMDB id, then to Home.
 */
fun ephyraOpenItem(
    packageName: String,
    item: ServiceItem,
): Intent =
    Intent(EPHYRA_ACTION_OPEN_ITEM)
        .setPackage(packageName)
        .addCategory(Intent.CATEGORY_DEFAULT)
        .apply {
            item.serverUrl?.let { putExtra(EPHYRA_EXTRA_SERVER_URL, it) }
            item.itemId?.let { putExtra(EPHYRA_EXTRA_ITEM_ID, it) }
            item.tmdbType?.let { putExtra(EPHYRA_EXTRA_TMDB_TYPE, it) }
            item.tmdbId?.let { putExtra(EPHYRA_EXTRA_TMDB_ID, it) }
        }

/**
 * The media server's base address from a link into its web client: everything before `/web`, or the origin when the link has none.
 * Null for something a browser cannot open.
 */
fun mediaServerBaseUrl(mediaUrl: String?): String? {
    val url = mediaUrl?.takeIf(::isOpenableSite) ?: return null
    val beforeWeb = url.substringBefore("/web")
    val origin = Regex("^https?://[^/?#]+").find(url)?.value
    return if (beforeWeb != url && beforeWeb.isNotBlank()) beforeWeb.trimEnd('/') else origin
}

/** Where a section's Open button goes. */
sealed interface OpenTarget {
    /** The user's own app for the service, opened with [intent]. */
    data class App(
        val app: ServiceApp,
        val intent: Intent,
    ) : OpenTarget {
        override fun equals(other: Any?) = other is App && other.app == app && other.intent.filterEquals(intent)

        override fun hashCode() = app.hashCode()
    }

    /** The service's web page. */
    data class Web(
        val url: String,
    ) : OpenTarget
}

/**
 * Where the section for [this] opens: its app when one answers an intent for [item], otherwise [webUrl] when it is an address a
 * browser can open, and null when neither, so the section has no button. [resolves] says whether some app answers an intent.
 */
fun ServiceKind.openTarget(
    item: ServiceItem,
    webUrl: String?,
    apps: Map<ServiceKind, ServiceApp> = serviceApps(),
    resolves: (Intent) -> Boolean,
): OpenTarget? {
    val app = apps[this]
    val intent = app?.intentsFor?.invoke(item)?.firstOrNull(resolves)
    return when {
        app != null && intent != null -> OpenTarget.App(app, intent)
        webUrl != null && isOpenableSite(webUrl) -> OpenTarget.Web(webUrl)
        else -> null
    }
}

/** Starts [target]'s app with its intent. */
fun Context.openServiceApp(target: OpenTarget.App) {
    attempt { startActivity(Intent(target.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Whether some app answers [intent]. The manifests' `<queries>` make an app that answers it visible; one that does not is not found. */
fun Context.canResolve(intent: Intent): Boolean = intent.resolveActivity(packageManager) != null
