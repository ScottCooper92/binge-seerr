package com.cooper.binge.companion.seerr.ui.hubs.core

import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * A hub of rows that load on their own. Nothing here names the app it serves: a row is whatever key type the caller passes,
 * its items whatever type its loader returns, and the loader is the caller's. It is written to move to the shared library;
 * the one JVM lock around the job table is the only platform call in it.
 */

/** One row that has something to show. */
data class HubRow<K, I>(
    val key: K,
    val items: List<I>,
)

/** What a hub is showing, from the rows that have resolved so far. */
sealed interface HubState<out K, out I> {
    /** No row has resolved yet. */
    data object Loading : HubState<Nothing, Nothing>

    /** At least one row has items. [pending] rows are still loading and fill in below as they land. */
    data class Ready<K, I>(
        val rows: List<HubRow<K, I>>,
        val pending: Int = 0,
    ) : HubState<K, I>

    /** Every row answered and none has anything to show, or there are no rows. */
    data object Empty : HubState<Nothing, Nothing>

    /** Every row failed. [error] is the first. */
    data class Failed(
        val error: Throwable,
    ) : HubState<Nothing, Nothing>
}

private sealed interface RowResult<out I> {
    data object Loading : RowResult<Nothing>

    data class Loaded<I>(
        val items: List<I>,
    ) : RowResult<I>

    data class Failed(
        val error: Throwable,
    ) : RowResult<Nothing>
}

private data class Data<K, I>(
    val keys: List<K> = emptyList(),
    val results: Map<K, RowResult<I>> = emptyMap(),
    val refreshing: Int = 0,
)

/**
 * Loads the rows of a hub independently. [show] says which rows are wanted and in what order; a row already loaded keeps its
 * items, so a reorder or a new row does not reload the rest. A row that fails or comes back empty is left out and never blocks
 * the others; only when every row fails is the hub [HubState.Failed]. [refresh] loads every row again, keeping the old items
 * on screen until the new ones land, and a row that fails on a refresh keeps its old items. [dispatcher] has no default.
 */
class HubLoader<K : Any, I>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val load: suspend (K) -> List<I>,
) {
    private val lock = Any()
    private var data = Data<K, I>()
    private val jobs = HashMap<K, Job>()
    private val stateFlow = MutableStateFlow<HubState<K, I>>(HubState.Loading)
    private val refreshingFlow = MutableStateFlow(false)

    val state: StateFlow<HubState<K, I>> = stateFlow.asStateFlow()

    /** Whether a [refresh] is still running, for a pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean> = refreshingFlow.asStateFlow()

    /** The rows wanted, in order. A row dropped is forgotten; a row new to the list starts loading. */
    fun show(keys: List<K>) {
        val wanted = keys.distinct()
        synchronized(lock) {
            jobs.keys.filter { it !in wanted }.forEach { jobs.remove(it)?.cancel() }
            val kept = data.results.filterKeys { it in wanted }
            change { it.copy(keys = wanted, results = kept + wanted.filter { key -> key !in kept }.associateWith { RowResult.Loading }) }
            wanted.filter { it !in jobs }.forEach { start(it, refresh = false) }
        }
    }

    /** Loads every wanted row again. */
    fun refresh() {
        synchronized(lock) {
            data.keys.forEach { key ->
                jobs.remove(key)?.cancel()
                start(key, refresh = true)
            }
        }
    }

    /** Applies [block] to the data and publishes what it now shows. The caller holds the lock. */
    private fun change(block: (Data<K, I>) -> Data<K, I>) {
        data = block(data)
        stateFlow.value = data.toState()
        refreshingFlow.value = data.refreshing > 0
    }

    private fun start(
        key: K,
        refresh: Boolean,
    ) {
        if (refresh) change { it.copy(refreshing = it.refreshing + 1) }
        jobs[key] =
            scope
                .launch(dispatcher) {
                    val result = attempt { load(key) }
                    synchronized(lock) {
                        if (key in data.keys) {
                            val previous = data.results[key]
                            val next =
                                result.fold(
                                    onSuccess = { RowResult.Loaded(it) },
                                    // A refresh that fails keeps what the row already showed.
                                    onFailure = { if (refresh && previous is RowResult.Loaded) previous else RowResult.Failed(it) },
                                )
                            change { it.copy(results = it.results + (key to next)) }
                        }
                    }
                }.also { job ->
                    if (refresh) {
                        job.invokeOnCompletion {
                            synchronized(
                                lock,
                            ) { change { it.copy(refreshing = (it.refreshing - 1).coerceAtLeast(0)) } }
                        }
                    }
                }
    }
}

private fun <K, I> Data<K, I>.toState(): HubState<K, I> {
    if (keys.isEmpty()) return HubState.Empty
    val resolved = keys.mapNotNull { key -> results[key] }
    val loaded = keys.mapNotNull { key -> (results[key] as? RowResult.Loaded)?.let { key to it.items } }.filter { it.second.isNotEmpty() }
    val pending = resolved.count { it is RowResult.Loading }
    return when {
        loaded.isNotEmpty() -> HubState.Ready(loaded.map { (key, items) -> HubRow(key, items) }, pending)
        pending > 0 -> HubState.Loading
        resolved.all { it is RowResult.Failed } -> HubState.Failed((resolved.first() as RowResult.Failed).error)
        else -> HubState.Empty
    }
}
