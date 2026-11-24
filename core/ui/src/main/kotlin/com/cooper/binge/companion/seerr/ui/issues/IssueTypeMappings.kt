package com.cooper.binge.companion.seerr.ui.issues

import androidx.annotation.StringRes
import com.cooper.binge.companion.seerr.core.ui.R
import com.cooper.binge.companion.seerr.ui.requests.IssueType

@StringRes
fun IssueType.labelRes(): Int =
    when (this) {
        IssueType.Video -> R.string.issue_type_video
        IssueType.Audio -> R.string.issue_type_audio
        IssueType.Subtitles -> R.string.issue_type_subtitles
        IssueType.Other -> R.string.issue_type_other
    }
