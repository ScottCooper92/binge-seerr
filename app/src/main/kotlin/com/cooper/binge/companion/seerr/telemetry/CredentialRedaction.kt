package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.diagnostics.Redactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Keeps [Redactor] told about what the saved connection holds: its address and its API key or session cookie, replaced wherever
 * they appear, and its host (with its port, and bare), replaced only as a whole token so a short host name cannot blank the
 * app's own text. Each change of connection forgets the previous literals first, so a literal that is gone stops being replaced.
 */
internal fun Redactor.followCredentials(
    credentials: Flow<SeerrCredentials?>,
    scope: CoroutineScope,
): Job =
    scope.launch {
        var held = HeldLiterals(emptyList(), emptyList())
        credentials.collect { current ->
            (held.anywhere + held.wholeToken).forEach { unregister(it) }
            held = current?.literals() ?: HeldLiterals(emptyList(), emptyList())
            held.anywhere.forEach { register(it) }
            held.wholeToken.forEach { registerWholeToken(it) }
        }
    }

private class HeldLiterals(
    val anywhere: List<String>,
    val wholeToken: List<String>,
)

private fun SeerrCredentials.literals(): HeldLiterals {
    val address = baseUrl.trim().trimEnd('/')
    val authority = address.substringAfter("://").substringBefore('/')
    val bareHost = address.toHttpUrlOrNull()?.host ?: authority.substringBefore(':')
    val secret =
        when (val credential = auth) {
            is SeerrAuth.ApiKey -> credential.key
            is SeerrAuth.Session -> credential.cookie
        }
    return HeldLiterals(
        anywhere = listOf(address, secret).filter { it.isNotBlank() }.distinct(),
        wholeToken = listOf(authority, bareHost).filter { it.isNotBlank() }.distinct(),
    )
}
