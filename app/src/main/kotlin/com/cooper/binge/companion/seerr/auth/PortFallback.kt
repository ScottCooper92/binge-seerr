package com.cooper.binge.companion.seerr.auth

import com.cooper.homelab.core.server.address.PortDefaults
import com.cooper.homelab.core.server.address.ServerAddress

/**
 * Runs [probe] against [baseUrl], and, when [baseUrl] carries no explicit port, retries against the same address on
 * [PortDefaults.SEERR] on any failure ([ServerAddress.candidates]) — an unreachable address or one that answered but not
 * as wanted. The retry's success wins; if it also fails, the first attempt's failure is what's reported, since that is the
 * address the user actually typed. Free of any dependency on [SeerrConnection] so the retry ordering is unit-testable with
 * no network at all.
 */
internal suspend fun <T> withPortFallback(
    baseUrl: String,
    probe: suspend (String) -> Result<T>,
): Result<T> {
    val primary = probe(baseUrl)
    if (primary.isSuccess) return primary
    val fallback =
        ServerAddress
            .parse(baseUrl)
            ?.candidates(httpPort = PortDefaults.SEERR)
            ?.drop(1)
            ?.firstOrNull() ?: return primary
    return probe(fallback.url).recoverCatching { primary.getOrThrow() }
}
