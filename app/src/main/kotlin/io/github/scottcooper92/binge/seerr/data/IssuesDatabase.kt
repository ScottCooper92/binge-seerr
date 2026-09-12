package io.github.scottcooper92.binge.seerr.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The issue browser's cache: the last pages of each list, so the browser opens on rows before the
 * server answers and reads them without it. It is the one source of truth for an issue's state,
 * so a resolve made on the issue's page moves its row at once. Cleared when the server changes.
 */
@Database(entities = [IssueEntity::class, IssueRemoteKeyEntity::class], version = 1, exportSchema = true)
abstract class IssuesDatabase : RoomDatabase() {
    abstract fun issueDao(): IssueDao

    abstract fun remoteKeyDao(): IssueRemoteKeyDao
}
