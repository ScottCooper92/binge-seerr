package com.cooper.binge.companion.seerr.telemetry

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.demo.demoCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedConnectionDemoSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy {
        CredentialStore(
            dataStore = PreferenceDataStoreFactory.create { folder.newFile("creds.preferences_pb") },
            cipher = PlainCipher,
            io = Dispatchers.Unconfined,
        )
    }

    private fun TestScope.session() =
        SavedConnectionDemoSession(
            store,
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
            ),
        )

    private fun credentials(baseUrl: String) = SeerrCredentials(baseUrl, SeerrAuth.ApiKey("k"), SeerrVariant.fromVersion("2.7.0"))

    @Test
    fun `nothing saved is not the demo, once it has been read`() =
        runTest {
            val session = session()
            session.awaitRead()
            assertFalse(session.isDemo)
        }

    @Test
    fun `the saved demo connection is the demo, and a real server replacing it is not`() =
        runTest {
            val session = session()
            store.save(demoCredentials())
            session.awaitRead()
            assertTrue(session.isDemo)

            store.save(credentials("https://seerr.example"))
            assertFalse(session.isDemo)
        }
}
