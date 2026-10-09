package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The paging sources a store has handed out and that are still valid, so the store can invalidate
 * them itself once one of its own writes commits.
 *
 * Room invalidates its paging sources from its invalidation tracker, but a source ignores the tracker
 * until its first load has returned. A write that commits after that load has read the table, and
 * before the load gets back to its caller, is dropped. The source keeps the rows from before the
 * write, and nothing invalidates it until the next write. A mediator's first refresh is exactly that
 * write: it waits for the cache's first read and commits as the read lets go. So a cold list could
 * stay on its skeleton for good (#900).
 *
 * Invalidating from the store, after the transaction commits, does not depend on that timing. Room's
 * own invalidation still runs too, and invalidating a source twice is harmless.
 */
class OpenPagingSources<Key : Any, Value : Any> {
    private val open = CopyOnWriteArraySet<PagingSource<Key, Value>>()

    /** Hands [source] out, kept until it is invalidated. */
    fun track(source: PagingSource<Key, Value>): PagingSource<Key, Value> {
        open += source
        source.registerInvalidatedCallback { open -= source }
        return source
    }

    /** Runs [write], then invalidates every source handed out, so each reads again what the write left. */
    suspend fun <T> afterWrite(write: suspend () -> T): T = write().also { open.forEach { it.invalidate() } }
}
