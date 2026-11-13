package com.cooper.binge.companion.seerr.review

import android.app.Activity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Gives the navigation host the one call it needs of [ReviewPrompt]. */
@HiltViewModel
class ReviewPromptViewModel
    @Inject
    constructor(
        private val prompt: ReviewPrompt,
    ) : ViewModel() {
        suspend fun onReturnedToRoot(activity: Activity) = prompt.maybeAsk(activity)

        fun askNow(activity: Activity) = prompt.askNow(activity)
    }
