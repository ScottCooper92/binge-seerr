package io.github.scottcooper92.binge.seerr.di

import io.github.scottcooper92.binge.seerr.data.IssueStore
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.data.RequestStore
import io.github.scottcooper92.binge.seerr.data.UserStore
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
