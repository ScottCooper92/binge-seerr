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
 * [flags]`.value`, which is always the latest resolved set.
 */
class RemoteFeatureFlags(
    private val source: RemoteFlagSource,
    overrides: FlagOverrides,
    scope: CoroutineScope,
) : FeatureFlags {
    private val remote = MutableStateFlow(source.current())

    override val flags: StateFlow<Flags> =
        combine(remote, overrides.values.onStart { emit(emptyMap()) }) { remote, local ->
            Flags(
                bingePromotion = local[Flag.BingePromotion] ?: remote.bingePromotion,
                requestCompanions = local[Flag.RequestCompanions] ?: remote.requestCompanions,
            )
        }.stateIn(scope, SharingStarted.Eagerly, remote.value)

    override suspend fun refresh() {
        source.fetch()?.let { remote.value = it }
    }
}
