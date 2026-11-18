package com.cooper.binge.companion.seerr.seerr

import retrofit2.HttpException
import retrofit2.Response

/** What `POST request` did, read off the status code: the one reading the exported Service and the in-app request sheet share. */
sealed interface RequestPostOutcome {
    /** A request was made. The id is absent where the server did not echo one. */
    data class Created(
        val requestId: Int?,
    ) : RequestPostOutcome

    /** The server took the call and created nothing, because everything asked for was already covered. */
    data object NothingToRequest : RequestPostOutcome

    /** Someone has already requested it. */
    data object AlreadyRequested : RequestPostOutcome
}

/** Reads the outcome off this response. Any other status is a refusal, thrown as the [HttpException] the callers classify. */
fun Response<SeerrRequestResultDto>.toOutcome(): RequestPostOutcome =
    when {
        code() == HTTP_ACCEPTED -> RequestPostOutcome.NothingToRequest
        isSuccessful -> RequestPostOutcome.Created(body()?.id)
        code() == HTTP_CONFLICT -> RequestPostOutcome.AlreadyRequested
        else -> throw HttpException(this)
    }
