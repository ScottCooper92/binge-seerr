package com.cooper.binge.companion.seerr.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cooper.binge.companion.seerr.di.flagOverridesDataStore
import com.cooper.binge.companion.seerr.notifications.SHARED_POLLING_OVERRIDE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Writes the switch that says whether the notification poll runs on the shared library's reactor, planner,
 * scheduler and cursor store. The choice is read once when the process starts, so it takes effect on the next launch:
 * `adb shell am broadcast -a <applicationId>.SHARED_POLLING --ez enabled true -n <applicationId>/.debug.SharedPollingSwitchReceiver`.
 * `--ez enabled false` turns it back off, and no `enabled` extra clears the override. Turning it off after a run on the shared
 * store makes the poll read the old, behind cursors again, so it announces the gap once.
 */
class SharedPollingSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val enabled = if (intent.hasExtra(EXTRA_ENABLED)) intent.getBooleanExtra(EXTRA_ENABLED, false) else null
        val key = booleanPreferencesKey(SHARED_POLLING_OVERRIDE)
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
