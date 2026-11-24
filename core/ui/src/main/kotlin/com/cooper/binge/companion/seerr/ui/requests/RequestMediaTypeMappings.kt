package com.cooper.binge.companion.seerr.ui.requests

import androidx.annotation.StringRes
import com.binge.designsystem.component.MediaTypeTagType
import com.cooper.binge.companion.seerr.core.ui.R

/** Doubles as the row's title when the lookup failed. */
@StringRes
fun RequestMediaType.labelRes(): Int =
    when (this) {
        RequestMediaType.Movie -> R.string.media_type_movie
        RequestMediaType.Tv -> R.string.media_type_tv
    }

/** Both are Movie/Tv enums, but distinct types — this app's request model and the design system's tag. */
fun RequestMediaType.toTagType(): MediaTypeTagType =
    when (this) {
        RequestMediaType.Movie -> MediaTypeTagType.Movie
        RequestMediaType.Tv -> MediaTypeTagType.Tv
    }
