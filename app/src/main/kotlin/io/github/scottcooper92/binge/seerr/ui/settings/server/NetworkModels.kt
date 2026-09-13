package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrDnsCacheSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMetadataProvidersDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMetadataSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMetadataTestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrNetworkSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrProxySettingsDto

private const val PORT_MAX = 65_535
private const val NO_TTL = -1

/**
 * The network form. Jellyseerr has the two switches; Seerr 3 adds the IPv4 preference, the proxy
 * and the DNS cache, each null where the server did not send it, so the form shows what the
 * server has and sends back no more.
 */
data class NetworkForm(
    val csrfProtection: Boolean = false,
    val trustProxy: Boolean = false,
    val forceIpv4First: Boolean? = null,
    val proxy: ProxyForm? = null,
    val dnsCache: DnsCacheForm? = null,
) {
    val valid: Boolean get() = proxy?.valid != false && dnsCache?.valid != false
}

/** The outbound proxy: reachable only while it has a host and a port in range, if it is on. */
data class ProxyForm(
    val enabled: Boolean = false,
    val host: String = "",
    val port: String = "",
    val useSsl: Boolean = false,
    val user: String = "",
    val password: String = "",
    val bypassFilter: String = "",
    val bypassLocalAddresses: Boolean = true,
) {
    val valid: Boolean get() = !enabled || (host.isNotBlank() && port.trim().toIntOrNull()?.let { it in 1..PORT_MAX } == true)
}

/** The DNS cache and the TTL bounds it forces; a blank bound is none. */
data class DnsCacheForm(
    val enabled: Boolean = false,
    val minTtl: String = "",
    val maxTtl: String = "",
) {
    val valid: Boolean get() = minTtl.isTtl() && maxTtl.isTtl()
}

private fun String.isTtl(): Boolean = isBlank() || trim().toIntOrNull()?.let { it >= 0 } == true

/** The metadata providers Seerr 3 can serve series and anime from. */
enum class MetadataProvider(
    val code: String,
) {
    Tmdb("tmdb"),
    Tvdb("tvdb"),
    ;

    companion object {
        fun fromCode(code: String?): MetadataProvider = entries.firstOrNull { it.code == code } ?: Tmdb
    }
}

data class MetadataForm(
    val tv: MetadataProvider = MetadataProvider.Tmdb,
    val anime: MetadataProvider = MetadataProvider.Tmdb,
) {
    /** A test reaches the providers the draft would use. */
    fun testBody(): SeerrMetadataTestBody =
        SeerrMetadataTestBody(
            tmdb = tv == MetadataProvider.Tmdb || anime == MetadataProvider.Tmdb,
            tvdb = tv == MetadataProvider.Tvdb || anime == MetadataProvider.Tvdb,
        )
}

data class MetadataExtras(
    val testing: Boolean = false,
)

internal fun SeerrNetworkSettingsDto.toForm(): NetworkForm =
    NetworkForm(
        csrfProtection = csrfProtection ?: false,
        trustProxy = trustProxy ?: false,
        forceIpv4First = forceIpv4First,
        proxy =
            proxy?.let {
                ProxyForm(
                    enabled = it.enabled,
                    host = it.hostname,
                    port = it.port.toString(),
                    useSsl = it.useSsl,
                    user = it.user,
                    password = it.password,
                    bypassFilter = it.bypassFilter,
                    bypassLocalAddresses = it.bypassLocalAddresses,
                )
            },
        dnsCache =
            dnsCache?.let {
                DnsCacheForm(
                    enabled = it.enabled,
                    minTtl =
                        it.forceMinTtl
                            .takeIf { ttl -> ttl > 0 }
                            ?.toString()
                            .orEmpty(),
                    maxTtl =
                        it.forceMaxTtl
                            .takeIf { ttl -> ttl >= 0 }
                            ?.toString()
                            .orEmpty(),
                )
            },
    )

internal fun NetworkForm.toDto(): SeerrNetworkSettingsDto =
    SeerrNetworkSettingsDto(
        csrfProtection = csrfProtection,
        trustProxy = trustProxy,
        forceIpv4First = forceIpv4First,
        proxy =
            proxy?.let {
                SeerrProxySettingsDto(
                    enabled = it.enabled,
                    hostname = it.host.trim(),
                    port = it.port.trim().toIntOrNull() ?: 0,
                    useSsl = it.useSsl,
                    user = it.user.trim(),
                    password = it.password,
                    bypassFilter = it.bypassFilter.trim(),
                    bypassLocalAddresses = it.bypassLocalAddresses,
                )
            },
        dnsCache =
            dnsCache?.let {
                SeerrDnsCacheSettingsDto(
                    enabled = it.enabled,
                    forceMinTtl = it.minTtl.trim().toIntOrNull() ?: 0,
                    forceMaxTtl = it.maxTtl.trim().toIntOrNull() ?: NO_TTL,
                )
            },
    )

internal fun SeerrMetadataSettingsDto.toForm(): MetadataForm =
    MetadataForm(tv = MetadataProvider.fromCode(settings.tv), anime = MetadataProvider.fromCode(settings.anime))

internal fun MetadataForm.toDto(): SeerrMetadataSettingsDto =
    SeerrMetadataSettingsDto(SeerrMetadataProvidersDto(tv = tv.code, anime = anime.code))
