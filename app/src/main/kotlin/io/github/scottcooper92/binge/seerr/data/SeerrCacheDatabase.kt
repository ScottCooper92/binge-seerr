package io.github.scottcooper92.binge.seerr.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The cache behind the browsers: the last pages of each issue list and each user list, so a
 * browser opens on rows before the server answers and reads them without it. It is the one
 * source of truth for a row's state, so a write made on a page moves its row at once. It is
 * cleared when the server changes, and a schema change rebuilds it rather than migrating: nothing
 * in it is the user's own.
 */
@Database(
    entities = [IssueEntity::class, IssueRemoteKeyEntity::class, UserEntity::class, UserRemoteKeyEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SeerrCacheDatabase : RoomDatabase() {
    abstract fun issueDao(): IssueDao

    abstract fun issueRemoteKeyDao(): IssueRemoteKeyDao

    abstract fun userDao(): UserDao

    abstract fun userRemoteKeyDao(): UserRemoteKeyDao
}
