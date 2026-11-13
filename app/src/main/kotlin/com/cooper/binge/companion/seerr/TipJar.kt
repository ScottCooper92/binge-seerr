package com.cooper.binge.companion.seerr

/**
 * Whether Settings offers the tip jar. Debug builds only for now: the sheet is a placeholder that
 * charges nothing, so a release build must not show it. Replaced by "Play returned the tip prices"
 * once the Billing layer exists, which also hides the row where Play cannot sell (TV, sideload).
 */
internal val TIP_JAR_AVAILABLE: Boolean = BuildConfig.DEBUG
