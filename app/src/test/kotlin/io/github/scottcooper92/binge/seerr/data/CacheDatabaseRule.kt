package io.github.scottcooper92.binge.seerr.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * The real [SeerrCacheDatabase], in memory, so a test runs the DAO's SQL rather than a fake's
 * reimplementation of it. Room's executors are real threads, so these tests block on them with
 * `runBlocking` rather than the test scheduler, and the rule must sit in a Robolectric test.
 */
class CacheDatabaseRule : ExternalResource() {
    lateinit var db: SeerrCacheDatabase
        private set

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SeerrCacheDatabase::class.java).build()
    }

    override fun after() {
        db.close()
    }
}
