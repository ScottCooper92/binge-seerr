package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.review.PlayReviewPromptLauncher
import com.cooper.binge.companion.seerr.review.ReviewPrompt
import com.cooper.binge.companion.seerr.review.ReviewPromptLauncher
import com.cooper.binge.companion.seerr.review.ReviewSignalSink
import com.cooper.binge.companion.seerr.review.ReviewSignalStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.reviewDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_review")

/** The review prompt's signals on the device, and Play's review card as the way to ask. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {
    @Binds
    abstract fun launcher(impl: PlayReviewPromptLauncher): ReviewPromptLauncher

    @Binds
    abstract fun signals(impl: ReviewPrompt): ReviewSignalSink

    companion object {
        @Provides
        @Singleton
        fun signalStore(
            @ApplicationContext context: Context,
        ): ReviewSignalStore = ReviewSignalStore(context.reviewDataStore)
    }
}
