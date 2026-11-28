package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrIssueTypeCode

/** What a user may report about a title, in the order the sheet offers them. */
enum class IssueType(
    val code: SeerrIssueTypeCode,
) {
    Video(SeerrIssueTypeCode.Video),
    Audio(SeerrIssueTypeCode.Audio),
    Subtitles(SeerrIssueTypeCode.Subtitles),
    Other(SeerrIssueTypeCode.Other),
}
