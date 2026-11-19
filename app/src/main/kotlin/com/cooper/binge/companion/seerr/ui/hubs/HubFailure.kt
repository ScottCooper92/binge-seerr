package com.cooper.binge.companion.seerr.ui.hubs

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.homelab.core.foundation.ServerFailure

/** The failure the shared hub loader hands back, as the screen's own error. A refusal is a rejection: a hub only reads, so no quota applies. */
internal fun ServerFailure.toSeerrError(): SeerrError =
    when (this) {
        ServerFailure.NotConnected -> SeerrError.NotConnected
        ServerFailure.SessionRejected -> SeerrError.Unauthorized
        ServerFailure.NotPermitted -> SeerrError.Forbidden
        ServerFailure.NotFound -> SeerrError.NotFound
        is ServerFailure.Refused -> SeerrError.Rejected
        ServerFailure.ServerError -> SeerrError.Server
        is ServerFailure.Unreachable -> SeerrError.Unreachable
        ServerFailure.LocalNetworkDenied -> SeerrError.LocalNetworkDenied
        else -> SeerrError.Unknown
    }
