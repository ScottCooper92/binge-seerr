package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.ui.NavDisplay
import com.binge.designsystem.motion.BingeNavMotion

/**
 * The design system's shared navigation motion, as the entry metadata Navigation 3 reads it from. A push reads the screen coming in and a pop
 * or a predictive pop the screen going out, so a set belongs to the entry on top, whatever is under it (pinned by `NavMotionSemanticsTest`).
 *
 * - [detail]: forward, pop and predictive pop, for a page that takes the whole window wherever it is opened from.
 * - [tab]: the lighter predictive pop alone, for a drill-down inside the Settings tab and the panes beside its hub. Its push and pop stay Navigation 3's own.
 *
 * Both read [BingeNavMotion] when a transition runs, so reduce motion and the layout direction apply as they change. An entry with neither
 * keeps Navigation 3's defaults.
 */
internal class NavMotionSpecs(
    val detail: Map<String, Any>,
    val tab: Map<String, Any>,
) {
    companion object {
        /** No motion metadata: Navigation 3's defaults everywhere, for a test that builds entries without a host. */
        val None = NavMotionSpecs(emptyMap(), emptyMap())
    }
}

/** The [NavMotionSpecs] over [motion], which is read each time a transition runs. */
internal fun navMotionSpecs(motion: () -> BingeNavMotion): NavMotionSpecs =
    NavMotionSpecs(
        detail =
            NavDisplay.transitionSpec { motion().detailForward } +
                NavDisplay.popTransitionSpec { motion().detailPop } +
                NavDisplay.predictivePopTransitionSpec { _ -> motion().detailPredictivePop },
        tab = NavDisplay.predictivePopTransitionSpec { _ -> motion().tabPredictivePop },
    )
