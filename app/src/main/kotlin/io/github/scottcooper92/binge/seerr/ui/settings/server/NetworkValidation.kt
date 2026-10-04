package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssue
import io.github.scottcooper92.binge.seerr.ui.users.settings.invalid
import io.github.scottcooper92.binge.seerr.ui.users.settings.missing

/** The section ids of the network page, shared by its validator and the composables that tag themselves with them. */
internal object NetworkSections {
    const val GENERAL = "general"
    const val PROXY = "proxy"
    const val DNS_CACHE = "dns_cache"
}

/** The field ids a [NetworkForm] issue can name. */
internal object NetworkFields {
    const val PROXY_HOST = "proxy_host"
    const val PROXY_PORT = "proxy_port"
    const val PROXY_USER = "proxy_user"
    const val PROXY_PASSWORD = "proxy_password"
    const val MIN_TTL = "min_ttl"
    const val MAX_TTL = "max_ttl"
}

internal const val NETWORK_FORM_KEY = "network"

/**
 * Everything standing between this draft and a save, in the order the sections read. A proxy or a
 * DNS cache that is off is not read, so it has no issues. It is empty exactly when [NetworkForm.valid]
 * is true, which `NetworkValidationTest` holds it to.
 */
internal fun NetworkForm.issues(): List<EditorIssue> = proxy?.issues().orEmpty() + dnsCache?.issues().orEmpty()

/**
 * A proxy that is on needs its host and a port in range. The username and password go together, so
 * one without the other is reported at once, as it was before the form had sections.
 */
private fun ProxyForm.issues(): List<EditorIssue> {
    if (!enabled) return emptyList()
    return buildList {
        if (host.isBlank()) add(missing(NetworkSections.PROXY, NetworkFields.PROXY_HOST))
        when {
            port.isBlank() -> add(missing(NetworkSections.PROXY, NetworkFields.PROXY_PORT))
            !hostAndPortValid("x", port) -> add(invalid(NetworkSections.PROXY, NetworkFields.PROXY_PORT, R.string.editor_error_port))
        }
        if (userMissing) add(invalid(NetworkSections.PROXY, NetworkFields.PROXY_USER, R.string.server_settings_proxy_user_missing))
        if (passwordMissing) {
            add(invalid(NetworkSections.PROXY, NetworkFields.PROXY_PASSWORD, R.string.server_settings_proxy_password_missing))
        }
    }
}

/** A bound is a whole number of seconds or blank, and the maximum must not be below the minimum. */
private fun DnsCacheForm.issues(): List<EditorIssue> {
    if (!enabled) return emptyList()
    return listOfNotNull(
        invalid(NetworkSections.DNS_CACHE, NetworkFields.MIN_TTL, R.string.server_settings_dns_ttl_hint).takeIf { !minTtl.isTtl() },
        when {
            !maxTtl.isTtl() -> invalid(NetworkSections.DNS_CACHE, NetworkFields.MAX_TTL, R.string.server_settings_dns_ttl_hint)
            !orderValid -> invalid(NetworkSections.DNS_CACHE, NetworkFields.MAX_TTL, R.string.server_settings_dns_ttl_order)
            else -> null
        },
    )
}
