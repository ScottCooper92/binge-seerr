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

/** `.local` is mDNS; `.lan`, `.home.arpa` and `.internal` are what home routers hand out. */
private val LOCAL_NETWORK_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")

private val LOCAL_NETWORK_ADDRESSES =
    listOf(
        // Link-local, and the RFC 1918 ranges.
        Regex("""169\.254(\.\d{1,3}){2}"""),
        Regex("""10(\.\d{1,3}){3}"""),
        Regex("""192\.168(\.\d{1,3}){2}"""),
        Regex("""172\.(1[6-9]|2\d|3[01])(\.\d{1,3}){2}"""),
        // IPv6 link-local and unique-local, bar Tailscale's own unique-local prefix.
        Regex("""(fe80|f[cd](?!7a:115c:a1e0:))[0-9a-f]*:.*"""),
    )

/**
 * Whether reaching [this] host needs the local-network permission: link-local, the RFC 1918 ranges,
 * IPv6 unique-local, the home-network suffixes and single-label names. Narrower than
 * [isLocalOrPrivateHost] on purpose. Loopback never leaves the device, and a tailnet address — the
 * CGNAT range, `.ts.net`, Tailscale's IPv6 prefix — goes out through the VPN interface, which the
 * platform does not count as local (#732). Judged on the name as typed, like that one.
 */
internal fun String.isLocalNetworkHost(): Boolean =
    LOCAL_NETWORK_SUFFIXES.any { endsWith(it) } ||
        LOCAL_NETWORK_ADDRESSES.any { it.matches(this) } ||
        (this != "localhost" && !contains('.') && !contains(':'))

/**
 * The host of this server address when reaching it needs the local-network permission, judged on the
 * name as typed by [isLocalNetworkHost]. Null for a public, loopback or tailnet host, and for an
 * address that does not parse.
 */
fun String.localNetworkHostOrNull(): String? = normaliseBaseUrl().toHttpUrlOrNull()?.host?.takeIf { it.isLocalNetworkHost() }

/** Whether [this] address needs the permission and [permission] does not hold it. */
fun String.isBlockedByLocalNetwork(permission: LocalNetworkPermission): Boolean =
    localNetworkHostOrNull() != null && !permission.isGranted()
