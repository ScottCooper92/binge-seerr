package io.github.scottcooper92.binge.seerr.ui.hub

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last good hub read, in memory, so returning to the hub shows the previous overview while the
 * refresh runs instead of a spinner. Not persisted: the overview goes stale quickly. It belongs to
 * one connection, address and sign-in together, because the overview carries the user's account and
 * permissions. `SeerrConnection`'s `onServerChanged` calls [clear] on every connect and disconnect,
 * like the other per-server caches, so a different server or a different account starts empty.
 */
@Singleton
class HubOverviewCache
    @Inject
    constructor() {
        private var generation = 0

        @Volatile var server: HubServer? = null
            private set

        @Volatile var overview: HubOverview? = null
            private set

        /** Read before a load starts and handed back to [remember], so a read begun before a [clear] cannot land after it. */
        @Synchronized
        fun generation(): Int = generation

        @Synchronized
        fun clear() {
            generation++
            server = null
            overview = null
        }

        /** Ignored when [clear] has run since [generation] was read: a slow read must not repopulate after a switch. */
        @Synchronized
        fun remember(
            generation: Int,
            server: HubServer? = null,
            overview: HubOverview? = null,
        ) {
            if (generation != this.generation) return
            server?.let { this.server = it }
            overview?.let { this.overview = it }
        }

        @Synchronized
        fun forgetOverview() {
            overview = null
        }
    }
