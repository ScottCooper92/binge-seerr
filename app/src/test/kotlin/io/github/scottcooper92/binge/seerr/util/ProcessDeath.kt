package io.github.scottcooper92.binge.seerr.util

import androidx.lifecycle.SavedStateHandle

/**
 * What the next view model's [SavedStateHandle] holds after the process is killed and its screen restored: the saved
 * values only. A test builds the new view model from it to show what survives (#1026).
 */
fun SavedStateHandle.afterProcessDeath(): SavedStateHandle = SavedStateHandle(keys().associateWith { get<Any?>(it) })
