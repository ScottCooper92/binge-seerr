package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.data.IssueStore
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.data.UserStore
import javax.inject.Inject

/** The stores of what one server returned, cleared together when the connected server changes. */
class ServerScopedCaches
    @Inject
    constructor(
        private val issues: IssueStore,
        private val requests: RequestStore,
        private val users: UserStore,
        private val statuses: MediaStatusStore,
    ) {
        suspend fun clearAll() {
            issues.clearAll()
            requests.clearAll()
            users.clearAll()
            statuses.clearAll()
        }
    }
