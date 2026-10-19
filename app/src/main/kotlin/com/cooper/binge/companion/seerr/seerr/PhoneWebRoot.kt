package com.cooper.binge.companion.seerr.seerr

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The root a link is written under when another device is to open it, such as the code a television shows: the server's
 * Application URL where it is a usable web address, else [baseUrl], the address this device uses. A phone on a VPN cannot
 * open the television's home-network address, and the Application URL is the one the administrator gave for reaching the
 * server from outside. Always ends in a slash. A link this device opens itself stays on [baseUrl].
 */
internal fun SeerrServerProfile.phoneWebRoot(baseUrl: String): String {
    val url = settings.applicationUrl?.trim()?.toHttpUrlOrNull()
    if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty()) return baseUrl
    return url.toString().let { if (it.endsWith('/')) it else "$it/" }
}
