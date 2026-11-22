package com.cooper.binge.companion.seerr.di

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

/**
 * What every preferences store does with a file it cannot parse: start again from empty. Without it a
 * corrupt file fails every later read the same way, and nothing ever repairs it. Empty is each store's
 * own default, and for the credentials it means signed out, as a failed decrypt already does.
 */
internal fun corruptionHandler(): ReplaceFileCorruptionHandler<Preferences> = ReplaceFileCorruptionHandler { emptyPreferences() }
