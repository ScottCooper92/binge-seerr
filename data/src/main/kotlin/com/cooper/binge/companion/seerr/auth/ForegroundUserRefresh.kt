package com.cooper.binge.companion.seerr.auth

import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-reads `auth/me` when the app comes back to the foreground, if the cached copy is over a minute old (#1722), so a
 * permission an admin changed meanwhile shows without a restart. With nothing saved there is nothing to read.
 */
class ForegroundUserRefresh(
    private val connection: SeerrConnection,
    private val scope: CoroutineScope,
) {
    fun onForeground() {
        scope.launch {
            if (connection.credentials.first() == null) return@launch
            attempt { connection.recentAuthenticatedUser() }
        }
    }
}
