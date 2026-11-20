package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.diagnostics.RedactionLiterals
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.follow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Keeps [Redactor] told about what the saved connection holds, through the shared library's `follow`: its address and its API key
 * or session cookie, replaced wherever they appear, and its host (with its port, and bare), replaced only as a whole token so a
 * short host name cannot blank the app's own text. A literal that is gone stops being replaced.
 */
internal fun Redactor.followCredentials(
    credentials: Flow<SeerrCredentials?>,
    scope: CoroutineScope,
): Job = follow(credentials.map { it?.literals() }, scope)

private fun SeerrCredentials.literals(): RedactionLiterals {
    val address = baseUrl.trim().trimEnd('/')
    val authority = address.substringAfter("://").substringBefore('/')
    val bareHost = address.toHttpUrlOrNull()?.host ?: authority.substringBefore(':')
    val secret =
        when (val credential = auth) {
            is SeerrAuth.ApiKey -> credential.key
            is SeerrAuth.Session -> credential.cookie
        }
    return RedactionLiterals(anywhere = listOf(address, secret), wholeToken = listOf(authority, bareHost))
}
