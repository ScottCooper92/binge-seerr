package com.cooper.binge.companion.seerr.ui.users

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which user's settings were just saved, for a page that shows what they set but reads it elsewhere (#1540). The user
 * page reads the quota from its own endpoint, and the General page beside it is what changes it, so the save says so here.
 */
@Singleton
class UserSettingsChanges
    @Inject
    constructor() {
        private val changed = MutableSharedFlow<Int>(extraBufferCapacity = CHANGE_BUFFER)
        val changes: SharedFlow<Int> = changed.asSharedFlow()

        /** [userId]'s settings were saved. */
        fun changed(userId: Int) {
            changed.tryEmit(userId)
        }

        private companion object {
            const val CHANGE_BUFFER = 8
        }
    }
