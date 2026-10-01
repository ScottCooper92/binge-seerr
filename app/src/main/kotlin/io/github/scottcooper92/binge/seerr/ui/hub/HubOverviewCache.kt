package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last good hub read, in memory, so returning to the hub shows the previous overview while the
 * refresh runs instead of a spinner. Not persisted: the overview goes stale quickly. It belongs to
 * one connection, address and sign-in together, because the overview carries the user's account and
 * permissions. So the entry carries the [SeerrCredentials] it was read under and a read hands its
 * own back: another connection's entry is never returned, whatever order a connect and a live
 * screen's reload run in. `SeerrConnection`'s `onServerChanged` also calls [clear] on every connect
 * and disconnect, like the other per-server caches, so nothing outlives the connection it came from.
 */
@Singleton
class HubOverviewCache
    @Inject
    constructor() {
        private var owner: SeerrCredentials? = null
        private var server: HubServer? = null
        private var overview: HubOverview? = null

        @Synchronized
        fun clear() {
            owner = null
            server = null
            overview = null
        }

        @Synchronized
        fun serverFor(credentials: SeerrCredentials?): HubServer? = server.takeIf { credentials != null && credentials == owner }

        @Synchronized
        fun overviewFor(credentials: SeerrCredentials?): HubOverview? = overview.takeIf { credentials != null && credentials == owner }

        /** A write under other credentials replaces the entry, so a slow read finishing late can only cost a miss. */
        @Synchronized
        fun remember(
            credentials: SeerrCredentials?,
            server: HubServer? = null,
            overview: HubOverview? = null,
        ) {
            if (credentials == null) return
            if (credentials != owner) {
                owner = credentials
                this.server = null
                this.overview = null
            }
            server?.let { this.server = it }
            overview?.let { this.overview = it }
        }

        /** Compare-and-clear: forgetting for one connection must not drop another's entry. */
        @Synchronized
        fun forgetOverview(credentials: SeerrCredentials?) {
            if (credentials != null && credentials == owner) overview = null
        }
    }
