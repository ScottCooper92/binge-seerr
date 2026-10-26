package com.cooper.binge.companion.seerr.flags

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** For the Activities that are not Hilt entry points of their own: the hand-offs, which only need to ask one question. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeatureFlagsEntryPoint {
    fun featureFlags(): FeatureFlags
}

/** The app's flags, from the Hilt graph; needs the real [android.app.Application]. */
fun Context.appFeatureFlags(): FeatureFlags =
    EntryPointAccessors.fromApplication(applicationContext, FeatureFlagsEntryPoint::class.java).featureFlags()
