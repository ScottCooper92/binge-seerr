package io.github.scottcooper92.binge.seerr

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The object graph is Hilt's, assembled from [io.github.scottcooper92.binge.seerr.di.SeerrModule].
 * A companion this size did not earn a DI framework; a client that manages the server it connects
 * to (#26) does, and the screens it inherits from Binge are shaped for one.
 */
@HiltAndroidApp
class SeerrApp : Application()
