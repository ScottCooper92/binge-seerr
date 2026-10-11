package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.request.v1.IssueType
import com.cooper.binge.companion.seerr.seerr.SeerrIssueTypeCode
import io.grpc.Status
import io.grpc.StatusException

internal fun invalidArgument(reason: String): StatusException = StatusException(Status.INVALID_ARGUMENT.withDescription(reason))

internal fun IssueType.toSeerrIssueType(): SeerrIssueTypeCode =
    when (this) {
        IssueType.ISSUE_TYPE_VIDEO -> SeerrIssueTypeCode.Video
        IssueType.ISSUE_TYPE_AUDIO -> SeerrIssueTypeCode.Audio
        IssueType.ISSUE_TYPE_SUBTITLE -> SeerrIssueTypeCode.Subtitles
        IssueType.ISSUE_TYPE_OTHER, IssueType.ISSUE_TYPE_UNSPECIFIED, IssueType.UNRECOGNIZED -> SeerrIssueTypeCode.Other
    }
