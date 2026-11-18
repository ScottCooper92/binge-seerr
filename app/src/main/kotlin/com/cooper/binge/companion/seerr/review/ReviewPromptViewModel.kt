package com.cooper.binge.companion.seerr.review

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.cooper.homelab.core.review.ReviewPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Holds the [ReviewPrompt] for the navigation host's return-to-root ask and the debug "ask now". */
@HiltViewModel
class ReviewPromptViewModel
    @Inject
    constructor(
        val prompt: ReviewPrompt,
    ) : ViewModel() {
        fun askNow(activity: Activity) = prompt.askNow(activity)
    }
