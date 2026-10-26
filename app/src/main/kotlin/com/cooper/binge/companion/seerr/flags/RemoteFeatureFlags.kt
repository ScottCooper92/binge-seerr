package com.cooper.binge.companion.seerr.flags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * [FeatureFlags] over a [RemoteFlagSource], with [overrides] on top. A caller that is not a UI reads
 * [flags]`.value`, which is always the latest resolved set. [initialOverrides] is what the overrides held at start, so
 * a read right after a cold start does not lose a race with the store.
 */
class RemoteFeatureFlags(
    private val source: RemoteFlagSource,
    overrides: FlagOverrides,
    scope: CoroutineScope,
    initialOverrides: Map<Flag, Boolean> = emptyMap(),
) : FeatureFlags {
    private val remote = MutableStateFlow(source.current())

    override val flags: StateFlow<Flags> =
        combine(remote, overrides.values.onStart { emit(initialOverrides) }) { remote, local -> resolve(remote, local) }
            .stateIn(scope, SharingStarted.Eagerly, resolve(remote.value, initialOverrides))

    override suspend fun refresh() {
        source.fetch()?.let { remote.value = it }
    }

    private fun resolve(
        remote: Flags,
        local: Map<Flag, Boolean>,
    ) = Flags(
        bingePromotion = local[Flag.BingePromotion] ?: remote.bingePromotion,
        requestCompanions = local[Flag.RequestCompanions] ?: remote.requestCompanions,
    )
}
