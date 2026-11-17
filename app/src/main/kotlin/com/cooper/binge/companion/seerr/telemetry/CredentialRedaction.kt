package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.diagnostics.Redactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Keeps [Redactor] told about what the saved connection holds: its address, its host, and its API key or session cookie.
 * Each change of connection forgets the previous literals first, so a literal that is gone stops being replaced.
 */
internal fun Redactor.followCredentials(
    credentials: Flow<SeerrCredentials?>,
    scope: CoroutineScope,
): Job =
    scope.launch {
        var held = emptyList<String>()
        credentials.collect { current ->
            held.forEach(::unregister)
            held = current?.literals().orEmpty()
            held.forEach(::register)
        }
    }

private fun SeerrCredentials.literals(): List<String> {
    val address = baseUrl.trim().trimEnd('/')
    val host = address.substringAfter("://").substringBefore('/')
    val secret =
        when (val credential = auth) {
            is SeerrAuth.ApiKey -> credential.key
            is SeerrAuth.Session -> credential.cookie
        }
    return listOf(address, host, secret).filter { it.isNotBlank() }.distinct()
}
