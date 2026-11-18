package com.cooper.binge.companion.seerr.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cooper.binge.companion.seerr.di.SERVER_REGISTRY_OVERRIDE
import com.cooper.binge.companion.seerr.di.flagOverridesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Writes the switch that says whether the saved connection lives in the shared library's per-server stores.
 * The broadcast must name the package (`-p <app id>`): from Android 8 a manifest receiver is not reached without it. The write is
 * asynchronous, so wait a couple of seconds before stopping the app. The choice is read once when the process starts, so it takes
 * effect on the next launch. `--ez enabled false` turns it back off,
 * and no `enabled` extra clears the override. The old file is never touched, so a flip back finds the connection as it was left
 * in the old file, and loses what was changed in the new stores.
 */
class ServerRegistrySwitchReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val enabled = if (intent.hasExtra(EXTRA_ENABLED)) intent.getBooleanExtra(EXTRA_ENABLED, false) else null
        val key = booleanPreferencesKey(SERVER_REGISTRY_OVERRIDE)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.applicationContext.flagOverridesDataStore.edit { if (enabled == null) it.remove(key) else it[key] = enabled }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_ENABLED = "enabled"
    }
}
