package com.cooper.binge.companion.seerr.seerr

import com.binge.companion.sdk.attempt
import java.time.Instant
import java.time.OffsetDateTime

/**
 * A Seerr timestamp as epoch milliseconds, or null for one this app cannot read.
 *
 * Two shapes, because the server sends both: most records carry a `Z`-suffixed instant, and some
 * carry a local time with an offset, which [Instant.parse] refuses.
 */
fun String.toEpochMillisOrNull(): Long? =
    attempt { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: attempt { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
