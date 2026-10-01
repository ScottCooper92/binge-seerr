package io.github.scottcooper92.binge.seerr.ui.hub

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last good hub read, in memory, so returning to the hub shows the previous overview while the
 * refresh runs instead of a spinner. Not persisted: the overview goes stale quickly. It belongs to
 * one server's address: [adopt] clears it when the address changes, so a different server starts
 * empty rather than showing the old one's counts and permissions.
 */
@Singleton
class HubOverviewCache
    @Inject
    constructor() {
        private var baseUrl: String? = null

        @Volatile var server: HubServer? = null
            private set

        @Volatile var overview: HubOverview? = null
            private set

        @Synchronized
        fun adopt(baseUrl: String?) {
            if (baseUrl == this.baseUrl) return
            this.baseUrl = baseUrl
            server = null
            overview = null
        }

        /** Ignored when [baseUrl] is no longer the adopted server: a slow read must not repopulate after a switch. */
        @Synchronized
        fun remember(
            baseUrl: String?,
            server: HubServer? = null,
            overview: HubOverview? = null,
        ) {
            if (baseUrl != this.baseUrl) return
            server?.let { this.server = it }
            overview?.let { this.overview = it }
        }

        @Synchronized
        fun forgetOverview() {
            overview = null
        }
    }
