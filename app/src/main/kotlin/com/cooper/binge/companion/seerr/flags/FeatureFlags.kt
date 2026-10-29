package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A switch for the Binge-facing surfaces, with the Remote Config key that carries it. Set in whichever Firebase project the build uses. */
enum class Flag(
    val key: String,
) {
    /** Anything that asks the user to install, open or connect Binge. */
    BingePromotion("binge_promotion_enabled"),
}

/**
 * Every flag at once. Off by default: a build that cannot reach a flag service says nothing about Binge. The REQUEST
 * Service is not flagged; a host that finds it can always use it.
 */
data class Flags(
    val bingePromotion: Boolean = false,
) {
    operator fun get(flag: Flag): Boolean =
        when (flag) {
            Flag.BingePromotion -> bingePromotion
        }

    companion object {
        val AllOn = Flags(bingePromotion = true)
    }
}

/** The current [Flags]: remote values, with debug overrides on top in a debug build. */
interface FeatureFlags {
    val flags: StateFlow<Flags>

    /** Fetches fresh remote values; the next value of [flags] carries them. A failed fetch keeps the last ones. */
    suspend fun refresh()
}

/** Fixed flags, for a test or a constructor default. It has no default of its own: the caller says which. */
class FixedFeatureFlags(
    value: Flags,
) : FeatureFlags {
    override val flags: StateFlow<Flags> = MutableStateFlow(value)

    override suspend fun refresh() = Unit
}

/** The flags for the composition below. Off until a root provides them, so a screen shown alone advertises nothing. */
val LocalFlags = staticCompositionLocalOf { Flags() }
