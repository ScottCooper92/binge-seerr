package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The store on the JVM, over a real DataStore in a temp file. */
class BingeConnectionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(scope: kotlinx.coroutines.CoroutineScope): BingeConnectionStore =
        DataStoreBingeConnectionStore(
            dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("binge.preferences_pb") },
        )

    @Test
    fun `reads false until a handshake is recorded`() =
        runTest {
            val store = store(backgroundScope)

            assertFalse(store.hasConnected.first())

            store.recordHandshake()

            assertTrue(store.hasConnected.first())
        }

    @Test
    fun `forgetting a recorded handshake reads false again`() =
        runTest {
            val store = store(backgroundScope)
            store.recordHandshake()

            store.forget()

            assertFalse(store.hasConnected.first())
        }

    @Test
    fun `a dismissed hint reads back, and survives forgetting the connection`() =
        runTest {
            val store = store(backgroundScope)
            assertEquals(emptySet<BingeHint>(), store.dismissedHints.first())

            store.dismissHint(BingeHint.Connected)
            store.recordHandshake()
            store.forget()

            assertEquals(setOf(BingeHint.Connected), store.dismissedHints.first())
        }
}
