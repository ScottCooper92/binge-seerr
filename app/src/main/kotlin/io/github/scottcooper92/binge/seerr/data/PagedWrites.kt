package io.github.scottcooper92.binge.seerr.data

import androidx.room.withTransaction

/**
 * The two-table writes the request, issue and user caches share: a list's rows and its cursor go in one transaction, so a
 * reader never sees rows without the cursor that says where they end. Each store hands over its own DAO calls and keeps
 * the DAOs per entity; what is common is the order and the transaction.
 */
internal class PagedWrites<E : Any>(
    private val db: SeerrCacheDatabase,
    private val sources: OpenPagingSources<Int, E>,
    private val clearList: suspend (listKey: String) -> Unit,
    private val upsertRows: suspend (List<E>) -> Unit,
    private val setCursor: suspend (listKey: String, nextSkip: Int?) -> Unit,
    private val clearEverything: suspend () -> Unit,
    private val clearCursors: suspend () -> Unit,
) {
    /** Replaces one list's slice with its first page; [nextSkip] is null when that page was the last. */
    suspend fun refresh(
        listKey: String,
        rows: List<E>,
        nextSkip: Int?,
    ) = sources.afterWrite {
        db.withTransaction {
            clearList(listKey)
            upsertRows(rows)
            setCursor(listKey, nextSkip)
        }
    }

    /** Adds one list's next page and moves its cursor on, to null at the last page. */
    suspend fun append(
        listKey: String,
        rows: List<E>,
        nextSkip: Int?,
    ) = sources.afterWrite {
        db.withTransaction {
            upsertRows(rows)
            setCursor(listKey, nextSkip)
        }
    }

    /** Every list's rows and cursors. */
    suspend fun clearAll() =
        sources.afterWrite {
            db.withTransaction {
                clearEverything()
                clearCursors()
            }
        }
}
