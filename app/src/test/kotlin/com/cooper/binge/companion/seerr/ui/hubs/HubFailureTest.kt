package com.cooper.binge.companion.seerr.ui.hubs

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.homelab.core.foundation.ServerFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class HubFailureTest {
    @Test
    fun `each failure the loader returns reads as the screen's own error`() {
        val expected =
            mapOf(
                ServerFailure.NotConnected to SeerrError.NotConnected,
                ServerFailure.SessionRejected to SeerrError.Unauthorized,
                ServerFailure.NotPermitted to SeerrError.Forbidden,
                ServerFailure.NotFound to SeerrError.NotFound,
                ServerFailure.Refused("no") to SeerrError.Rejected,
                ServerFailure.ServerError to SeerrError.Server,
                ServerFailure.Unreachable() to SeerrError.Unreachable,
                ServerFailure.LocalNetworkDenied to SeerrError.LocalNetworkDenied,
                ServerFailure.Unreadable to SeerrError.Unknown,
                ServerFailure.Unknown to SeerrError.Unknown,
            )

        expected.forEach { (failure, error) -> assertEquals(failure.toString(), error, failure.toSeerrError()) }
    }
}
