package com.cooper.binge.companion.seerr.notifications

import javax.inject.Qualifier

/** The scope that outlives every screen, for the planner. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
