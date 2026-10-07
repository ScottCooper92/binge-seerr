package io.github.scottcooper92.binge.seerr.handoff

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * The Wi-Fi or Ethernet network this device is on, beneath any VPN: where a phone and a TV in the same room can
 * reach each other. Not the active network, which with Tailscale or another VPN up is the tunnel — and a VPN reports
 * the Wi-Fi it rides on as one of its own transports, so it has to be ruled out by name.
 */
internal fun Context.localNetwork(): Network? {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return null
    val candidates =
        listOfNotNull(connectivity.activeNetwork) +
            @Suppress("DEPRECATION")
            connectivity.allNetworks
    return candidates.firstOrNull { network ->
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return@firstOrNull false
        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
    }
}
