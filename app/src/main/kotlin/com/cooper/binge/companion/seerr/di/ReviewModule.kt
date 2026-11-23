package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.reviewPrompt
import com.cooper.homelab.core.foundation.AppDispatchers
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.review.PlayReviewPromptLauncher
import com.cooper.homelab.core.review.ReviewPrompt
import com.cooper.homelab.core.review.ReviewPromptLauncher
import com.cooper.homelab.core.review.ReviewSignalSink
import com.cooper.homelab.core.review.ReviewSignalStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

private val Context.reviewDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_review",
    corruptionHandler = corruptionHandler(),
)

/** The review prompt's signals on the device, and Play's review card as the way to ask. */
@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {
    @Provides
    @Singleton
    fun signalStore(
        @ApplicationContext context: Context,
    ): ReviewSignalStore = ReviewSignalStore(context.reviewDataStore)

    @Provides
    @Singleton
    fun launcher(
        @ApplicationContext context: Context,
    ): ReviewPromptLauncher = PlayReviewPromptLauncher(context, AppDispatchers.platform().main)

    @Provides
    @Singleton
    fun reviewPrompt(
        store: ReviewSignalStore,
        launcher: ReviewPromptLauncher,
        featureFlags: FeatureFlags,
        @ApplicationScope scope: CoroutineScope,
    ): ReviewPrompt =
        ReviewPrompt(
            store = store,
            launcher = launcher,
            isEnabled = { featureFlags.flags.value.reviewPrompt },
            scope = scope,
            clock = Clock.system(),
        )

    @Provides
    fun signals(prompt: ReviewPrompt): ReviewSignalSink = prompt
}
