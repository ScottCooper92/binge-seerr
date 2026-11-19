package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.data.IssueStore
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.UserStore
import com.cooper.homelab.core.presentation.search.RecentSearches
import javax.inject.Inject

/**
 * The stores of what one server returned, cleared together when the connected server changes, and with them the settings search's
 * recents, which name pages of one account.
 */
class ServerScopedCaches
    @Inject
    constructor(
        private val issues: IssueStore,
        private val requests: RequestStore,
        private val users: UserStore,
        private val statuses: MediaStatusStore,
        private val titleStatuses: TitleStatusStore,
        private val recentSearches: RecentSearches,
    ) {
        suspend fun clearAll() {
            issues.clearAll()
            requests.clearAll()
            users.clearAll()
            statuses.clearAll()
            titleStatuses.clear()
            recentSearches.clear()
        }
    }
