package com.cooper.binge.companion.seerr.tip

import android.app.Activity

/** The tip amounts, in whole pounds: one Play product per step, `tip_1` to `tip_20`. */
internal val TIP_STEPS: IntRange = 1..20

internal fun tipProductId(step: Int): String = "tip_$step"

/** How a tip ended, as far as the user needs to hear. A cancel is the user's own choice and says nothing. */
internal enum class TipOutcome { Thanks, Pending, Cancelled, Failed }

/**
 * What the tip jar needs from the store, so no screen or ViewModel touches the Play client. Play sells fixed
 * products only, which is why an amount is a [TIP_STEPS] step and never a free-typed sum.
 */
internal interface TipBilling {
    /** The local price of every step, or null where tips cannot be sold: no Play, offline, or products not live. */
    suspend fun prices(): Map<Int, String>?

    /** Buys [step] and returns how it ended; a bought tip is consumed so the same step can be bought again. */
    suspend fun give(
        activity: Activity,
        step: Int,
    ): TipOutcome
}
