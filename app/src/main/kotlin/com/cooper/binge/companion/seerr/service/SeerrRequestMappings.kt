package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.IssueType
import com.cooper.binge.companion.seerr.seerr.SeerrIssueTypeCode
import io.grpc.Status
import io.grpc.StatusException

internal fun invalidArgument(reason: String): StatusException = StatusException(Status.INVALID_ARGUMENT.withDescription(reason))

/**
 * A contract request id as Seerr's own: the contract's ids are opaque strings, and Seerr's are numbers. An id
 * this server could never have issued is the contract's NOT_FOUND, the answer for any unknown request.
 */
internal fun String.toSeerrRequestId(): Int =
    toIntOrNull() ?: throw StatusException(Status.NOT_FOUND.withDescription("No request with that id"))

internal fun IssueType.toSeerrIssueType(): SeerrIssueTypeCode =
    when (this) {
        IssueType.ISSUE_TYPE_VIDEO -> SeerrIssueTypeCode.Video
        IssueType.ISSUE_TYPE_AUDIO -> SeerrIssueTypeCode.Audio
        IssueType.ISSUE_TYPE_SUBTITLE -> SeerrIssueTypeCode.Subtitles
        IssueType.ISSUE_TYPE_OTHER, IssueType.ISSUE_TYPE_UNSPECIFIED, IssueType.UNRECOGNIZED -> SeerrIssueTypeCode.Other
    }
