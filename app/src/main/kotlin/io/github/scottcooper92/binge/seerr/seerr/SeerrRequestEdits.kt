package io.github.scottcooper92.binge.seerr.seerr

import retrofit2.HttpException
import retrofit2.Response

/** Seerr's status for a request write that went through and created or changed nothing. */
private const val HTTP_ACCEPTED = 202

/**
 * Seerr's answer to an edit that leaves nothing to request: the seasons named are all on the server already, or another
 * request holds them. It is a 202 with `{"message":"No seasons available to request"}`, and nothing changed (#1001). A
 * success code for a refusal, so it is its own failure: the contract's FAILED_PRECONDITION, final rather than worth a retry.
 */
class NothingLeftToRequestException(
    response: Response<*>,
) : HttpException(response)

/** [SeerrApi.editRequest], with every refusal thrown: a failure code as an [HttpException], and Seerr's 202 as [NothingLeftToRequestException]. */
suspend fun SeerrApi.updateRequest(
    requestId: Int,
    body: SeerrEditRequestBody,
) {
    val response = editRequest(requestId, body)
    when {
        response.code() == HTTP_ACCEPTED -> throw NothingLeftToRequestException(response)
        !response.isSuccessful -> throw HttpException(response)
    }
}
