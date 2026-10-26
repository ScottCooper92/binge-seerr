package com.cooper.binge.companion.seerr.flags

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A switch in this app that Binge's rollout decides, with the Remote Config key that carries it. */
enum class Flag(
    val key: String,
) {
    /** Anything that asks the user to install, open or connect Binge. */
    BingePromotion("binge_promotion_enabled"),

    /** Anything that only works if Binge's REQUEST companions are on: the Service, its hand-offs, notice forwarding. */
    RequestCompanions("request_companions_enabled"),
}

/** Every flag at once. Both default off: a build that cannot reach a flag service behaves as if Binge were absent. */
data class Flags(
    val bingePromotion: Boolean = false,
    val requestCompanions: Boolean = false,
) {
    /**
     * Whether to talk about *connecting* to Binge. It needs both flags: never advertise a connection the
     * integration cannot make. Installing and opening Binge need only [bingePromotion].
     */
    val bingeConnectPromotion: Boolean get() = bingePromotion && requestCompanions

    operator fun get(flag: Flag): Boolean =
        when (flag) {
            Flag.BingePromotion -> bingePromotion
            Flag.RequestCompanions -> requestCompanions
        }

    companion object {
        val AllOn = Flags(bingePromotion = true, requestCompanions = true)
    }
}

/** The current [Flags]: remote values, with debug overrides on top in a debug build. */
interface FeatureFlags {
    val flags: StateFlow<Flags>

    /** Fetches fresh remote values; the next value of [flags] carries them. A failed fetch keeps the last ones. */
    suspend fun refresh()
}

/** Fixed flags — what a test, a preview or an Activity with no graph is given. */
class FixedFeatureFlags(
    value: Flags = Flags.AllOn,
) : FeatureFlags {
    override val flags: StateFlow<Flags> = MutableStateFlow(value)

    override suspend fun refresh() = Unit
}

/** The flags for the composition below. Off until a root provides them, so a screen shown alone advertises nothing. */
val LocalFlags = staticCompositionLocalOf { Flags() }
