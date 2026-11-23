package com.cooper.binge.companion.seerr.auth

import com.cooper.homelab.core.server.connection.CallOutcome
import com.cooper.homelab.core.server.connection.CallResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SeerrAuthFailureClassifierTest {
    private val proxy = listOf("settings", "radarr", "test")
    private val classifier = SeerrAuthFailureClassifier { it == proxy }

    private fun answered(
        status: Int,
        path: List<String> = listOf("request"),
        sessionRejected: Boolean = false,
    ) = classifier.classify(CallResult.Answered(status, path, sessionRejected))

    @Test
    fun `a cancelled call says nothing about the server`() {
        assertEquals(CallOutcome.Ignore, classifier.classify(CallResult.TransportFailure(cancelled = true)))
    }

    @Test
    fun `any other transport failure is no answer`() {
        assertEquals(CallOutcome.NoAnswer, classifier.classify(CallResult.TransportFailure()))
    }

    @Test
    fun `a session the app found gone is a refusal`() = assertEquals(CallOutcome.AuthRejected, answered(401, sessionRejected = true))

    @Test
    fun `a 500 on a proxy route is an answer`() = assertEquals(CallOutcome.Answered, answered(500, proxy))

    @Test
    fun `a 500 elsewhere is no answer`() = assertEquals(CallOutcome.NoAnswer, answered(500))

    @Test
    fun `a 503 is no answer`() = assertEquals(CallOutcome.NoAnswer, answered(503))

    @Test
    fun `a 429 is no answer`() = assertEquals(CallOutcome.NoAnswer, answered(429))

    @Test
    fun `a permission 403 means the credential was accepted`() = assertEquals(CallOutcome.Answered, answered(403))

    @Test
    fun `a 404 means the credential was accepted`() = assertEquals(CallOutcome.Answered, answered(404))
}
