package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.handoff.AddressHandOffSession
import com.cooper.binge.companion.seerr.handoff.AddressHandOffs
import com.cooper.binge.companion.seerr.handoff.HandOffOpening
import kotlin.time.Duration

/** An opener that records the listener handed on to outlive a screen, instead of serving it. */
internal class RecordingHandOffs(
    private val session: AddressHandOffSession,
) : AddressHandOffs {
    @Volatile
    var lingered: AddressHandOffSession? = null

    override fun open(): HandOffOpening = HandOffOpening.Opened(session)

    override fun linger(
        session: AddressHandOffSession,
        within: Duration,
    ) {
        lingered = session
    }
}
