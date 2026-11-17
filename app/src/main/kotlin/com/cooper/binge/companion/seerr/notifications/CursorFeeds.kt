package com.cooper.binge.companion.seerr.notifications

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.notifications.Feed
import com.cooper.homelab.core.notifications.FeedCursorKey
import com.cooper.homelab.core.notifications.FeedCursorStore
import com.cooper.homelab.core.notifications.FeedRunner
import com.cooper.homelab.core.notifications.LongCursorCodec
import com.cooper.homelab.core.notifications.FeedRead as SharedFeedRead

/**
 * The poll's cursor feeds, the signals that read "what is newer than the last id I saw": fetch, announce, advance. Which store
 * holds the cursors is the one thing that differs between [PrefsCursorFeeds], today's, and [SharedCursorFeeds].
 */
interface CursorFeeds {
    /**
     * Checks one feed. A seed run takes the separate [seed] read, which titles nothing because it announces nothing; an empty
     * feed seeds to 0, a real cursor, so the first row to arrive is announced rather than seeding again. After that the cursor
     * follows the newest **raw** id the server offered, not the newest announced row, so a row titling drops cannot hold it back.
     * A failed read leaves the cursor where it was, so the next run catches up.
     */
    suspend fun <T> check(
        signal: NotificationSignal,
        userId: Int,
        seed: suspend () -> Int,
        fetch: suspend (sinceId: Int) -> FeedRead<T>,
        notify: suspend (List<T>) -> Unit,
    ): CheckResult
}

/** The cursors where they have always been: one int preference per signal in [NotificationPrefs]. */
class PrefsCursorFeeds(
    private val prefs: NotificationPrefs,
) : CursorFeeds {
    override suspend fun <T> check(
        signal: NotificationSignal,
        userId: Int,
        seed: suspend () -> Int,
        fetch: suspend (sinceId: Int) -> FeedRead<T>,
        notify: suspend (List<T>) -> Unit,
    ): CheckResult {
        val cursor = prefs.cursor(signal)
        if (cursor == null) {
            val newest = attempt { seed() }.getOrElse { return it.toResult() }
            prefs.setCursor(signal, newest)
            return CheckResult.Ok
        }
        val read = attempt { fetch(cursor) }.getOrElse { return it.toResult() }
        if (read.rows.isNotEmpty()) notify(read.rows)
        if (read.newestId > cursor) prefs.setCursor(signal, read.newestId)
        return CheckResult.Ok
    }
}

/**
 * The cursors in the shared library's per-server store, run by its [FeedRunner]. The first run for a signal imports the cursor
 * [PrefsCursorFeeds] left, so turning the switch on announces nothing twice and misses nothing. The old preference is left where
 * it is and is no longer read here; turning a signal on drops it (and this one) as it always has.
 */
class SharedCursorFeeds(
    private val prefs: NotificationPrefs,
    private val store: FeedCursorStore,
    private val server: suspend () -> ServerId,
    private val pollUser: PollUserStore,
    private val logger: Logger,
) : CursorFeeds {
    override suspend fun <T> check(
        signal: NotificationSignal,
        userId: Int,
        seed: suspend () -> Int,
        fetch: suspend (sinceId: Int) -> FeedRead<T>,
        notify: suspend (List<T>) -> Unit,
    ): CheckResult {
        val serverId = server()
        pollUser.set(userId.toString())
        val key = FeedCursorKey(serverId, userId.toString(), signal.key)
        if (store.get(key) == null) prefs.cursor(signal)?.let { store.set(key, it.toString()) }
        val feed =
            object : Feed<T, Long> {
                override val signal: String = signal.key

                override suspend fun seed(): Long = seed().toLong()

                override suspend fun fetchSince(cursor: Long): SharedFeedRead<T, Long> {
                    val read = fetch(cursor.toInt())
                    return SharedFeedRead(read.rows, read.newestId.toLong())
                }

                override suspend fun announce(rows: List<T>) = notify(rows)
            }
        val runner = FeedRunner(feed, LongCursorCodec, store, serverId, userId.toString(), { it.toResult().toShared() }, logger)
        return runner.run().toSeerr()
    }
}
