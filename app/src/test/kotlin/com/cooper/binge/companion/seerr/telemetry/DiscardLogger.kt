package com.cooper.binge.companion.seerr.telemetry

import com.cooper.homelab.core.foundation.Logger

/** For a test that does not look at what is logged. */
internal val DiscardLogger: Logger = Logger { _, _, _, _, _ -> }
