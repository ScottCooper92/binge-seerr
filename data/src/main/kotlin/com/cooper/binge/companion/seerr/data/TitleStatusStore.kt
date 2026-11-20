package com.cooper.binge.companion.seerr.data

import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Which kind of title a status is for. Seerr keeps a movie and a show with the same TMDB id apart. */
enum class TitleType { Movie, Tv }

data class TitleKey(
    val type: TitleType,
    val tmdbId: Int,
)

/** A title's latest known status, standard and 4K apart as Seerr keeps them. [mediaId] is the server's own id once it has one. */
data class TitleStatuses(
    val standard: SeerrMediaStatusCode? = null,
    val fourK: SeerrMediaStatusCode? = null,
    val mediaId: Int? = null,
    /** Whether the signed-in user has the title on their watchlist, or null where nothing read has said. */
    val onWatchlist: Boolean? = null,
) {
    fun statusOf(is4k: Boolean): SeerrMediaStatusCode? = if (is4k) fourK else standard

    fun with(
        is4k: Boolean,
        status: SeerrMediaStatusCode?,
    ): TitleStatuses = if (is4k) copy(fourK = status) else copy(standard = status)
}

/** Reads a title's statuses off the `mediaInfo` the server sent with it; a title the server does not know has none. */
fun SeerrMediaInfoDto?.toTitleStatuses(): TitleStatuses = TitleStatuses(this?.status, this?.status4k, this?.id)

/** What [TitleStatusStore.applyOptimistic] gives back, so a failed action can [TitleStatusStore.rollback] exactly its own write. */
class StatusUndo internal constructor(
    internal val key: TitleKey,
    internal val previous: Entry?,
    internal val stamp: Long,
)

/** One stored value and the stamp that orders it against server reads. */
internal data class Entry(
    val statuses: TitleStatuses,
    val stamp: Long,
)

/**
 * The latest known status of every title this process has seen, which every list of cards follows so a request, a block or a
 * removal shows wherever the title appears. It is memory only: the server is the truth and the next read corrects it, so
 * nothing here needs to outlive the process (the host's own cache is [MediaStatusStore]).
 *
 * Ordering is by a clock that ticks on every write and every [beginRead]. A server read is stamped when it begins, so an
 * answer that was in flight while an action wrote cannot undo the action, and a newer read wins over an older one.
 */
class TitleStatusStore {
    private val clock = AtomicLong()
    private val entries = MutableStateFlow<Map<TitleKey, Entry>>(emptyMap())

    /** Every known title's statuses. A list overlays its cards from this. */
    val statuses: Flow<Map<TitleKey, TitleStatuses>> =
        entries.map { all -> all.mapValues { it.value.statuses } }.distinctUntilChanged()

    /** What the store holds for [key] right now. */
    fun current(key: TitleKey): TitleStatuses? = entries.value[key]?.statuses

    /** Call before sending a read, and pass the result to [seed] with the answer. */
    fun beginRead(): Long = clock.incrementAndGet()

    /** Records what a server read said, unless an action or a newer read has written the title since [readStamp]. */
    fun seed(
        key: TitleKey,
        statuses: TitleStatuses,
        readStamp: Long,
    ) {
        entries.update { current ->
            val existing = current[key]
            // A read that does not say whether the title is on the watchlist leaves what an earlier one said.
            val merged = if (statuses.onWatchlist == null) statuses.copy(onWatchlist = existing?.statuses?.onWatchlist) else statuses
            if (existing != null && existing.stamp > readStamp) current else current + (key to Entry(merged, readStamp))
        }
    }

    /** The server's own answer to an action: it replaces whatever is there, optimistic or not. */
    fun confirm(
        key: TitleKey,
        statuses: TitleStatuses,
    ) {
        val stamp = clock.incrementAndGet()
        entries.update { it + (key to Entry(statuses, stamp)) }
    }

    /** Shows an action's likely result at once. [base] is what the title holds when nothing is known yet. */
    fun applyOptimistic(
        key: TitleKey,
        base: TitleStatuses = TitleStatuses(),
        change: (TitleStatuses) -> TitleStatuses,
    ): StatusUndo {
        val stamp = clock.incrementAndGet()
        var previous: Entry? = null
        entries.update { current ->
            previous = current[key]
            current + (key to Entry(change(previous?.statuses ?: base), stamp))
        }
        return StatusUndo(key, previous, stamp)
    }

    /** Puts back what [undo] replaced, but only while nothing has written the title since: a newer write is not ours to undo. */
    fun rollback(undo: StatusUndo) {
        entries.update { current ->
            if (current[undo.key]?.stamp != undo.stamp) {
                current
            } else if (undo.previous == null) {
                current - undo.key
            } else {
                current + (undo.key to undo.previous)
            }
        }
    }

    /** The server or the signed-in user changed, so nothing known is known. */
    fun clear() {
        entries.value = emptyMap()
    }
}
