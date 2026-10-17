package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.hasExplicitPort
import com.cooper.binge.companion.seerr.seerr.withDefaultSeerrPort

/**
 * Runs [probe] against [baseUrl], and, when [baseUrl] carries no explicit port, retries against
 * [String.withDefaultSeerrPort] on any failure — an unreachable address or one that answered but
 * not as wanted. The retry's success wins; if it also fails, the first attempt's failure is what's
 * reported, since that is the address the user actually typed. Free of any dependency on
 * [SeerrConnection] so the retry ordering is unit-testable with no network at all.
 */
internal suspend fun <T> withPortFallback(
    baseUrl: String,
    probe: suspend (String) -> Result<T>,
): Result<T> {
    val primary = probe(baseUrl)
    if (primary.isSuccess || baseUrl.hasExplicitPort()) return primary
    val fallbackUrl = baseUrl.withDefaultSeerrPort() ?: return primary
    return probe(fallbackUrl).recoverCatching { primary.getOrThrow() }
}
