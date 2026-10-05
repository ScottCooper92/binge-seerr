package io.github.scottcooper92.binge.seerr.seerr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The first SDK with the `ACCESS_LOCAL_NETWORK` runtime permission. */
private const val LOCAL_NETWORK_SDK = 37

/**
 * Whether this app may reach hosts on the user's own network. From SDK 37 a connection to a private
 * address, a `.local` or `.lan` name or a single-label name is refused until the user grants it,
 * and an app targeting 37 no longer gets it automatically. Before 37 there is nothing to ask for.
 */
fun interface LocalNetworkPermission {
    /** True where a connection to the local network is allowed, including where the platform has no such permission. */
    fun isGranted(): Boolean

    companion object {
        /** For a build, test or preview where nothing is ever refused. */
        val AlwaysGranted: LocalNetworkPermission = LocalNetworkPermission { true }
    }
}

internal class AndroidLocalNetworkPermission(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : LocalNetworkPermission {
    override fun isGranted(): Boolean =
        sdkInt < LOCAL_NETWORK_SDK ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
}

/** The platform permission to ask for, which exists only on [LOCAL_NETWORK_SDK] and above. */
internal const val LOCAL_NETWORK_PERMISSION: String = Manifest.permission.ACCESS_LOCAL_NETWORK

/**
 * The host of this server address when reaching it needs the local-network permission, judged on the
 * name as typed with the same line [isLocalOrPrivateHost] draws for plain HTTP. Null for a public
 * host, and for an address that does not parse.
 */
fun String.localNetworkHostOrNull(): String? = normaliseBaseUrl().toHttpUrlOrNull()?.host?.takeIf { it.isLocalOrPrivateHost() }

/** Whether [this] address needs the permission and [permission] does not hold it. */
fun String.isBlockedByLocalNetwork(permission: LocalNetworkPermission): Boolean =
    localNetworkHostOrNull() != null && !permission.isGranted()
