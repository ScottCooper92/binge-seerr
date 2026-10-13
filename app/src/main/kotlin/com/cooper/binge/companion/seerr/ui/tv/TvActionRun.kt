package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * and is ignored. So every action routed through a run must emit exactly one answer: one that returns early without
 * emitting would leave the status at Saving. A surface that owns a run [clear]s it when it goes away, so its status is not
 * the next surface's.
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

    /**
     * A new place for the status to stand, such as another issue's sheet or the Mark as sheet: whatever the last place left
     * behind, a failure and its Retry, is not this place's to show. An action still saving is kept, so its answer lands.
     */
    fun clearUnlessSaving() {
        if (status != SaveStatus.Saving) clear()
    }
}

/**
 * Gives this status a fresh start each time the place it stands in appears ([key] changes, or the sheet opens) and
 * clears it when that place goes, so a Back out of a failed sheet never leaves its Retry for the next one (#1519).
 *
 * [keepSaving] says whether an action still saving survives that. It does where the action belongs to the place that
 * comes next, as Mark as's does to its page. It does not where the run is shared across items, as the issue sheets'
 * is: a late answer must find the run idle and be ignored, not settle another issue's.
 */
@Composable
internal fun TvActionRun.ScopedTo(
    key: Any?,
    keepSaving: Boolean = true,
) {
    LaunchedEffect(key) { if (keepSaving) clearUnlessSaving() else clear() }
    DisposableEffect(this) { onDispose { if (keepSaving) clearUnlessSaving() else clear() } }
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
