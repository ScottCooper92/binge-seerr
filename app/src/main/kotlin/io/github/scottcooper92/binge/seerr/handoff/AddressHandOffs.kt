package io.github.scottcooper92.binge.seerr.handoff

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.scottcooper92.binge.seerr.R
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Locale
import javax.inject.Inject

/** What asking for a hand-off came to. */
sealed interface HandOffOpening {
    data class Opened(
        val session: AddressHandOffSession,
    ) : HandOffOpening

    /** The device has no private IPv4 address on Wi-Fi or Ethernet, so no phone could reach it. */
    data object NoLocalNetwork : HandOffOpening
}

/** Opens a hand-off listener on the television. Called off the main thread. */
fun interface AddressHandOffs {
    /** Throws an `IOException` when the listener cannot be opened on a network the device does have. */
    fun open(): HandOffOpening
}

/**
 * The first private IPv4 address among [this]: what a phone on the same Wi-Fi can reach. IPv6 is
 * left out because a QR code with a scoped or temporary address in it is a code a phone often
 * cannot use.
 */
internal fun List<InetAddress>.lanIpv4(): Inet4Address? = filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }

/**
 * Opens [AddressHandOffListener]s on the TV's address on the active Wi-Fi or Ethernet network, on a
 * port the system picks. A VPN, a mobile network or no network at all reads as no local network: a
 * phone could not reach the TV through any of them.
 */
internal class LanAddressHandOffs
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AddressHandOffs {
        override fun open(): HandOffOpening {
            val address = lanAddress() ?: return HandOffOpening.NoLocalNetwork
            // A backlog of one: the listener answers one connection at a time, and a queue is no help.
            val server = ServerSocket(0, 1, address)
            val target = TvHandOffTarget(host = checkNotNull(address.hostAddress), port = server.localPort, token = newHandOffToken())
            val page =
                HandOffPageTemplate(
                    copyFor = ::copyFor,
                    appLink = TvHandOffLinks.intentUrl(target, context.packageName, fallbackUrl = target.url),
                )
            return HandOffOpening.Opened(AddressHandOffListener(server, target.token, target.url, page))
        }

        private fun lanAddress(): Inet4Address? {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = connectivity.activeNetwork ?: return null
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
            val local =
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!local) return null
            return connectivity
                .getLinkProperties(network)
                ?.linkAddresses
                ?.map { it.address }
                ?.lanIpv4()
        }

        /** The page's strings in [language]. A bundle install without that language's split falls back to English. */
        private fun copyFor(language: String): HandOffPageCopy {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val resources = context.createConfigurationContext(configuration).resources
            val appName = resources.getString(R.string.companion_label)
            return HandOffPageCopy(
                language = language,
                title = resources.getString(R.string.handoff_page_title),
                body = resources.getString(R.string.handoff_page_body),
                field = resources.getString(R.string.handoff_page_field),
                placeholder = resources.getString(R.string.handoff_page_placeholder),
                send = resources.getString(R.string.handoff_page_send),
                invalid = resources.getString(R.string.handoff_page_invalid),
                appBody = resources.getString(R.string.handoff_page_app_body, appName),
                openApp = resources.getString(R.string.handoff_page_open_app, appName),
                sentTitle = resources.getString(R.string.handoff_page_sent_title),
                sentBody = resources.getString(R.string.handoff_page_sent_body),
            )
        }
    }
