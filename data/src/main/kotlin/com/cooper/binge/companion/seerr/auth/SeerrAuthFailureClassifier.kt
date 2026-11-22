package com.cooper.binge.companion.seerr.auth

import com.cooper.homelab.core.server.connection.AuthFailureClassifier
import com.cooper.homelab.core.server.connection.CallOutcome
import com.cooper.homelab.core.server.connection.CallResult

private const val HTTP_SERVER_ERROR = 500
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Seerr's rules. A session the app's code found gone is a refusal. A transport failure, a 429 or a 5xx is no answer, except a 500 on a
 * route Seerr answers by calling a Radarr or Sonarr instance live ([isProxyRoute]), which is an answer: the instance behind it failed.
 * Every other status, a permission 403 included, means the server accepted the credential.
 */
class SeerrAuthFailureClassifier(
    private val isProxyRoute: (List<String>) -> Boolean,
) : AuthFailureClassifier {
    override fun classify(result: CallResult): CallOutcome =
        when (result) {
            is CallResult.TransportFailure -> if (result.cancelled) CallOutcome.Ignore else CallOutcome.NoAnswer
            is CallResult.Answered ->
                when {
                    result.sessionRejected -> CallOutcome.AuthRejected
                    result.status == HTTP_SERVER_ERROR && isProxyRoute(result.path) -> CallOutcome.Answered
                    result.status == HTTP_TOO_MANY_REQUESTS || result.status >= HTTP_SERVER_ERROR -> CallOutcome.NoAnswer
                    else -> CallOutcome.Answered
                }
        }
}
