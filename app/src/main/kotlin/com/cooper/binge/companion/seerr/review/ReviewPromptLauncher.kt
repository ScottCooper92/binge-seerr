package com.cooper.binge.companion.seerr.review

import android.app.Activity
import android.content.Context
import com.binge.companion.sdk.attempt
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Asks Google Play to show its in-app review card over an activity. Play may show nothing. */
fun interface ReviewPromptLauncher {
    suspend fun launch(activity: Activity)
}

/**
 * [ReviewPromptLauncher] over Play's In-App Review API. It only translates to that API and awaits its tasks. Play limits
 * how often the card appears and says nothing when it does not, so a failure of either call is simply no card. The
 * launch runs on Main and is skipped for an activity that went away while the request was in flight.
 */
class PlayReviewPromptLauncher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ReviewPromptLauncher {
        override suspend fun launch(activity: Activity) {
            attempt {
                val manager = ReviewManagerFactory.create(context)
                val info = manager.requestReviewFlow().await()
                // The flow starts an activity: do it on Main, and only over one that is still there after the wait.
                withContext(Dispatchers.Main) {
                    if (!activity.isFinishing && !activity.isDestroyed) manager.launchReviewFlow(activity, info).await()
                }
            }
        }
    }
