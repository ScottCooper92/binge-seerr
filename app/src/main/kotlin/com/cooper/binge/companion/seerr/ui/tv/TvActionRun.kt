package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.binge.designsystem.SaveStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** How long "Saved" stays up before a sheet that was saving closes, long enough to read and short enough not to be waited on. */
internal const val TV_SAVED_HOLD_MILLIS = 800L

/**
 * What a television page's last action is doing, as the design system's [SaveStatus]: saving, saved, or failed with the
 * attempt kept so Try again can run it once more (#1510). It stands where the action lives, in its sheet or under its row,
 * and stays until the user acts again, so a failure is never a message that times out before a remote can reach it.
 *
 * An action [run]s through here. The ViewModel's one-shot answer then [settle]s it: the first answer after a run is that
 * run's, since a ViewModel refuses a second while one is in flight. An answer with nothing running is some other screen's
 * and is ignored.
 */
@Stable
internal class TvActionRun(
    initial: SaveStatus = SaveStatus.Idle,
) {
    var status by mutableStateOf(initial)
        private set

    private var attempt: (() -> Unit)? = null

    /** Starts [attempt], and keeps it for [again]. */
    fun run(attempt: () -> Unit) {
        this.attempt = attempt
        status = SaveStatus.Saving
        attempt()
    }

    /** Runs the last attempt once more: the Try again of a failure. */
    fun again() {
        attempt?.let(::run)
    }

    /** The run's answer. A failure may name what Try again should run, where the action that failed is not the one that ran. */
    fun settle(
        failed: Boolean,
        retry: (() -> Unit)? = null,
    ) {
        if (status != SaveStatus.Saving) return
        if (retry != null) attempt = retry
        status = if (failed) SaveStatus.Failed else SaveStatus.Saved
    }

    /** Nothing to show: the status cleared once it has been read. */
    fun clear() {
        status = SaveStatus.Idle
        attempt = null
    }
}

/**
 * A [TvActionRun] settled by [events]: each one reaches [answer], which says what it means with [TvActionRun.settle].
 * [initial] is a status already showing on arrival, for a frame, which never collects [events].
 */
@Composable
internal fun <E : Any> rememberTvActionRun(
    events: Flow<E>,
    initial: SaveStatus = SaveStatus.Idle,
    answer: TvActionRun.(E) -> Unit,
): TvActionRun {
    val run = remember { TvActionRun(initial) }
    val latest by rememberUpdatedState(answer)
    LaunchedEffect(events) { events.collect { run.latest(it) } }
    return run
}

/** Clears a "Saved" once it has been read, and runs [then]: how a sheet that saved in place closes. */
@Composable
internal fun TvActionRun.OnSaved(then: () -> Unit) {
    val latest by rememberUpdatedState(then)
    LaunchedEffect(status) {
        if (status == SaveStatus.Saved) {
            delay(TV_SAVED_HOLD_MILLIS)
            clear()
            latest()
        }
    }
}

/** Clears a "Saved" once it has been read, for a line that stays on its page. */
@Composable
internal fun TvActionRun.ClearWhenSaved() {
    LaunchedEffect(status) {
        if (status == SaveStatus.Saved) {
            delay(TV_SAVED_HOLD_MILLIS)
            clear()
        }
    }
}
