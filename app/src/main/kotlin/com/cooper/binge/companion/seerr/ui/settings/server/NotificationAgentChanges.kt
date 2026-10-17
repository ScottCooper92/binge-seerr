package com.cooper.binge.companion.seerr.ui.settings.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which agent was just saved on the server, for the agents list to read again (#1556). An agent's page saves as it changes
 * and sends a change still waiting when it closes, so a read made as the page closes can pass that write on the way. This is
 * said only once the server has taken the write, so a read made on it sees the change.
 */
@Singleton
class NotificationAgentChanges
    @Inject
    constructor() {
        private val changed = MutableSharedFlow<ServerAgent>(extraBufferCapacity = CHANGE_BUFFER)
        val changes: SharedFlow<ServerAgent> = changed.asSharedFlow()

        /** The server has taken a write of [agent]. */
        fun changed(agent: ServerAgent) {
            changed.tryEmit(agent)
        }

        private companion object {
            const val CHANGE_BUFFER = 8
        }
    }
