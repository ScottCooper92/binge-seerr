package io.github.scottcooper92.binge.seerr.data

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs [block] with this flag set, and clears it however the block ends, cancellation included. A caller reads the
 * flag to leave a running read alone rather than start a second (#1193).
 */
internal inline fun <T> AtomicBoolean.whileSet(block: () -> T): T {
    set(true)
    try {
        return block()
    } finally {
        set(false)
    }
}
