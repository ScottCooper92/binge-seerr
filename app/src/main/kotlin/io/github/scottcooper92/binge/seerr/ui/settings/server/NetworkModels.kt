package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrDnsCacheSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMetadataSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMetadataTestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrNetworkSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrProxySettingsDto
import java.math.BigDecimal
import java.math.RoundingMode

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
    /** Seconds the server waits on an external service, as the web client shows it; null where the server has no such setting. */
    val apiRequestTimeout: String? = null,
) {
    /** A number of seconds, 0 or more, decimals allowed: the web client's rule, and 0 means no timeout. */
    val apiRequestTimeoutValid: Boolean get() = apiRequestTimeout == null || apiRequestTimeout.isTimeoutSeconds()

    val valid: Boolean get() = proxy?.valid != false && dnsCache?.valid != false && apiRequestTimeoutValid
}

/**
 * The entry as a number of seconds, or null where it is not one. Either `.` or `,` is the decimal separator: the app
 * ships in English and Spanish, which write `1.5` and `1,5`, and a decimal pad may offer either (#890).
 */
internal fun String.toTimeoutSeconds(): BigDecimal? = canonicalSeconds().toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

/** The entry with `.` as its separator, the form the draft keeps and the server's number is read from. */
internal fun String.canonicalSeconds(): String = trim().replace(',', '.')

/** Canonical seconds as written where [separator] is the decimal separator. */
internal fun String.withDecimalSeparator(separator: Char): String = replace('.', separator)

/** The seconds as the whole milliseconds the server stores, or null where they are not a number or would not fit a [Long]. */
internal fun String.toTimeoutMillis(): Long? =
    toTimeoutSeconds()?.let { seconds ->
        seconds
            .multiply(MILLIS_PER_SECOND)
            .setScale(0, RoundingMode.HALF_UP)
            .takeIf { it <= BigDecimal.valueOf(Long.MAX_VALUE) }
            ?.toLong()
    }

internal fun String.isTimeoutSeconds(): Boolean = toTimeoutMillis() != null

/** Milliseconds as the seconds the web client shows: 1500 reads as `1.5`, 30000 as `30`. */
internal fun Long.toTimeoutSeconds(): String =
    BigDecimal
        .valueOf(this)
        .movePointLeft(MILLIS_SCALE)
        .stripTrailingZeros()
        .toPlainString()

private const val MILLIS_SCALE = 3
private val MILLIS_PER_SECOND = BigDecimal(1000)

/** The outbound proxy: reachable only while it has a host and a port in range, with its credentials as a pair, if it is on. */
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
    val addressValid: Boolean get() = hostAndPortValid(host, port)

    /** The username and password go together: a proxy that is given one without the other cannot authenticate. */
    val userMissing: Boolean get() = user.isBlank() && password.isNotEmpty()
    val passwordMissing: Boolean get() = user.isNotBlank() && password.isEmpty()

    val valid: Boolean get() = !enabled || (addressValid && !userMissing && !passwordMissing)
}

/** The DNS cache and the TTL bounds it forces; a blank bound is none. */
data class DnsCacheForm(
    val enabled: Boolean = false,
    val minTtl: String = "",
    val maxTtl: String = "",
) {
    /** Both bounds, when both are set, must not cross: a minimum above the maximum can never be met. */
    val orderValid: Boolean
        get() {
            val min = minTtl.trim().toIntOrNull()
            val max = maxTtl.trim().toIntOrNull()
            return min == null || max == null || min <= max
        }

    val valid: Boolean get() = !enabled || (minTtl.isTtl() && maxTtl.isTtl() && orderValid)
}

internal fun String.isTtl(): Boolean = isBlank() || trim().toIntOrNull()?.let { it >= 0 } == true

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

/** What a provider's last test said, as the web client shows it: untested until a test reaches it. */
enum class ProviderCheck { NotTested, Operational, Failed }

data class MetadataExtras(
    val testing: Boolean = false,
    val tmdb: ProviderCheck = ProviderCheck.NotTested,
    val tvdb: ProviderCheck = ProviderCheck.NotTested,
)

internal fun String?.toProviderCheck(): ProviderCheck =
    when (this) {
        "ok" -> ProviderCheck.Operational
        "failed" -> ProviderCheck.Failed
        else -> ProviderCheck.NotTested
    }

internal fun SeerrNetworkSettingsDto.toForm(): NetworkForm =
    NetworkForm(
        csrfProtection = csrfProtection ?: false,
        trustProxy = trustProxy ?: false,
        forceIpv4First = forceIpv4First,
        apiRequestTimeout = apiRequestTimeout?.toTimeoutSeconds(),
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
        apiRequestTimeout = apiRequestTimeout?.toTimeoutMillis(),
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
    MetadataForm(tv = MetadataProvider.fromCode(tv), anime = MetadataProvider.fromCode(anime))

internal fun MetadataForm.toDto(): SeerrMetadataSettingsDto = SeerrMetadataSettingsDto(tv = tv.code, anime = anime.code)
