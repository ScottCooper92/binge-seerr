package io.github.scottcooper92.binge.seerr.handoff

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
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
 * Opens [AddressHandOffListener]s on the TV's address on its Wi-Fi or Ethernet network, on a port the
 * system picks. That is the network beneath any VPN ([localNetwork]): a TV running Tailscale is still
 * on the room's Wi-Fi. A mobile network or no network at all reads as no local network.
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
            val target =
                TvHandOffTarget(
                    host = checkNotNull(address.hostAddress),
                    port = server.localPort,
                    token = newHandOffToken(),
                )
            val page =
                HandOffPageTemplate(
                    copyFor = ::copyFor,
                    appLink = TvHandOffLinks.intentUrl(target, context.packageName, fallbackUrl = target.url),
                    token = target.token,
                )
            return HandOffOpening.Opened(AddressHandOffListener(server, target.token, target.url, page, key = HandOffKey.generate()))
        }

        private fun lanAddress(): Inet4Address? {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = context.localNetwork() ?: return null
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
            // The product's name, not this build's launcher label: a phone gets the store app, so "Seerr Debug" from a
            // debug TV build would name something the phone can't install.
            val appName = resources.getString(R.string.handoff_page_app_name)
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
                install =
                    resources.getString(
                        R.string.handoff_page_install,
                        appName,
                        resources.getString(R.string.handoff_page_store_name),
                    ),
                storeName = resources.getString(R.string.handoff_page_store_name),
                sentTitle = resources.getString(R.string.handoff_page_sent_title),
                sentBody = resources.getString(R.string.handoff_page_sent_body),
                confirmTitle = resources.getString(R.string.handoff_page_confirm_title),
                confirmBody = resources.getString(R.string.handoff_page_confirm_body),
                pinTitle = resources.getString(R.string.handoff_page_pin_title),
                pinBody = resources.getString(R.string.handoff_page_pin_body),
                pinField = resources.getString(R.string.handoff_page_pin_field),
                pinSubmit = resources.getString(R.string.handoff_page_pin_submit),
                pinWrong = resources.getString(R.string.handoff_page_pin_wrong),
                pinLocked = resources.getString(R.string.handoff_page_pin_locked),
                failed = resources.getString(R.string.handoff_page_failed),
                signInTitle = resources.getString(R.string.handoff_page_signin_title),
                signInBody = { server -> resources.getString(R.string.handoff_page_signin_body, server) },
                connectedTitle = resources.getString(R.string.handoff_page_connected_title),
                connectedBody = resources.getString(R.string.handoff_page_connected_body),
                signInFormTitle = { server -> resources.getString(R.string.handoff_page_signin_form_title, server) },
                signInFormBody = resources.getString(R.string.handoff_page_signin_form_body),
                modeField = resources.getString(R.string.handoff_page_mode),
                modeLabel = { mode ->
                    when (mode) {
                        "Local" -> resources.getString(R.string.handoff_page_mode_local)
                        "ApiKey" -> resources.getString(R.string.handoff_page_mode_api_key)
                        else -> mode
                    }
                },
                username = resources.getString(R.string.handoff_page_username),
                email = resources.getString(R.string.handoff_page_email),
                password = resources.getString(R.string.handoff_page_password),
                apiKey = resources.getString(R.string.handoff_page_mode_api_key),
                signIn = resources.getString(R.string.handoff_page_sign_in),
                signingIn = resources.getString(R.string.handoff_page_signing_in),
                rejected = resources.getString(R.string.handoff_page_rejected),
            )
        }
    }
