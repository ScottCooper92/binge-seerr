package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.handoff.AddressLocality
import io.github.scottcooper92.binge.seerr.handoff.AddressSender
import io.github.scottcooper92.binge.seerr.handoff.AddressSource
import io.github.scottcooper92.binge.seerr.handoff.DataStoreHandOffAddressMemory
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TOKEN = "abcdefghijklmnopqrstuv"
private const val LINK = "seerr-companion://tv-handoff?to=192.168.86.53:41234&token=$TOKEN"

/**
 * The phone's confirmation: it refuses a link that does not name a TV on the LAN, says when there is
 * nothing to send, offers the addresses it knows best first, and sends the chosen one — and nothing
 * of the session — only when asked to.
 */
class SendAddressViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()

    /** Each ViewModel gets a store file of its own: DataStore refuses two on one file. */
    private var stores = 0

    @After
    fun tearDown() = viewModels.clear()

    /** Every send, with what it carried; answers [accept] until a test changes it. */
    private class RecordingSender : AddressSender {
        val sent = mutableListOf<Pair<TvHandOffTarget, String>>()
        var accept = CompletableDeferred(true)

        override suspend fun send(
            target: TvHandOffTarget,
            address: String,
        ): Boolean {
            sent += target to address
            return accept.await()
        }
    }

    private val sender = RecordingSender()

    private val memory = DataStoreHandOffAddressMemory(InMemoryDataStore())

    private suspend fun TestScope.viewModel(
        link: String?,
        saved: SeerrCredentials? = null,
        applicationUrl: String? = null,
    ): SendAddressViewModel {
        val store =
            CredentialStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c${stores++}.preferences_pb") },
                PlainCipher,
            )
        saved?.let { store.save(it) }
        val vm =
            SendAddressViewModel(
                connection = SeerrConnection(store, SeerrApiFactory(logRequests = false)),
                sender = sender,
                applicationUrl = { applicationUrl },
                memory = memory,
                dispatcher = mainDispatcherRule.dispatcher,
                link = link,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        return vm
    }

    private suspend fun SendAddressViewModel.settled(): SendAddressUiState = uiState.first { it != SendAddressUiState.Loading }

    @Test
    fun `a link to a public address, a name or this phone is refused, and nothing is sent`() =
        runTest {
            val saved = SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("secret"))
            listOf(
                "seerr-companion://tv-handoff?to=8.8.8.8:80&token=$TOKEN",
                "seerr-companion://tv-handoff?to=evil.example.com:80&token=$TOKEN",
                "seerr-companion://tv-handoff?to=127.0.0.1:8080&token=$TOKEN",
                "seerr-companion://tv-handoff?to=192.168.1.2:80&token=bad",
                null,
            ).forEach { link ->
                assertEquals(link, SendAddressUiState.Refused, viewModel(link, saved).settled())
            }
            assertTrue(sender.sent.isEmpty())
        }

    @Test
    fun `a phone with no server says there is nothing to send`() =
        runTest {
            assertEquals(SendAddressUiState.NotConnected, viewModel(LINK).settled())
        }

    @Test
    fun `the confirmation names the address and the TV, and sends only the address`() =
        runTest {
            val saved = SeerrCredentials("http://admin:hunter2@seerr.lan:5055/?x=1#y", SeerrAuth.ApiKey("secret-key"))
            val vm = viewModel(LINK, saved)

            val ready = vm.settled() as SendAddressUiState.Ready
            assertEquals(listOf("http://seerr.lan:5055/"), ready.candidates.map { it.address })
            assertTrue(ready.isSingle)
            assertEquals("192.168.86.53", ready.tv)
            assertTrue(sender.sent.isEmpty())

            vm.send()

            assertEquals(SendAddressUiState.Sent(tv = "192.168.86.53"), vm.uiState.first { it is SendAddressUiState.Sent })
            val (target, address) = sender.sent.single()
            assertEquals("http://192.168.86.53:41234/a/$TOKEN", target.url)
            assertEquals("http://seerr.lan:5055/", address)
        }

    @Test
    fun `a TV that does not take it says so, and the user can try again`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("https://seerr.example.com/", SeerrAuth.ApiKey("k")))
            vm.settled()
            sender.accept = CompletableDeferred()

            vm.send()
            assertTrue((vm.uiState.value as SendAddressUiState.Ready).isSending)
            vm.send()
            sender.accept.complete(false)

            val failed = vm.uiState.first { it is SendAddressUiState.Ready && it.failed } as SendAddressUiState.Ready
            assertEquals(false, failed.isSending)
            assertEquals(1, sender.sent.size)

            sender.accept = CompletableDeferred(true)
            vm.send()
            assertEquals(SendAddressUiState.Sent(tv = "192.168.86.53"), vm.uiState.first { it is SendAddressUiState.Sent })
        }

    @Test
    fun `the Application URL is offered beside the phone's own, and a local one is chosen first`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("https://seerr.example.com/", SeerrAuth.ApiKey("k")), "http://192.168.1.10:5055")

            val ready = vm.settled() as SendAddressUiState.Ready
            assertFalse(ready.isSingle)
            assertEquals(
                listOf(AddressSource.ApplicationUrl, AddressSource.Connected),
                ready.candidates.map { it.source },
            )
            assertEquals(AddressChoice.Candidate("http://192.168.1.10:5055/"), ready.choice)
            assertTrue(sender.sent.isEmpty())
        }

    @Test
    fun `an Application URL that is the phone's own address is not offered twice`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("http://192.168.1.10:5055/", SeerrAuth.ApiKey("k")), "HTTP://192.168.1.10:5055")

            assertTrue((vm.settled() as SendAddressUiState.Ready).isSingle)
        }

    @Test
    fun `a not-local address alone is still the one shown, and another can be chosen instead`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("http://100.101.102.103:5055/", SeerrAuth.ApiKey("k")))
            val ready = vm.settled() as SendAddressUiState.Ready
            assertTrue(ready.isSingle)
            assertEquals(AddressLocality.NotLocal, ready.candidates.single().locality)

            vm.choose(AddressChoice.Other)
            val choosing = vm.uiState.value as SendAddressUiState.Ready
            assertFalse(choosing.isSingle)
            assertEquals("http://:5055", choosing.otherAddress)
        }

    @Test
    fun `a typed address is checked, sent only on the tap, and offered first next time`() =
        runTest {
            val saved = SeerrCredentials("http://100.101.102.103:5055/", SeerrAuth.ApiKey("k"))
            val vm = viewModel(LINK, saved)
            vm.settled()
            vm.choose(AddressChoice.Other)

            vm.editOther("http://:5055")
            vm.send()
            assertTrue((vm.uiState.value as SendAddressUiState.Ready).otherInvalid)
            assertTrue(sender.sent.isEmpty())

            vm.editOther("http://192.168.1.10:5055")
            assertTrue(sender.sent.isEmpty())
            vm.send()

            vm.uiState.first { it is SendAddressUiState.Sent }
            assertEquals("http://192.168.1.10:5055/", sender.sent.single().second)
            assertEquals(listOf("http://192.168.1.10:5055/"), memory.remembered("http://100.101.102.103:5055/"))

            val next = viewModel(LINK, saved).settled() as SendAddressUiState.Ready
            assertEquals(AddressChoice.Candidate("http://192.168.1.10:5055/"), next.choice)
            assertEquals(AddressSource.Remembered, next.candidates.first().source)
        }

    @Test
    fun `a TV that refuses a typed address does not have it remembered`() =
        runTest {
            val saved = SeerrCredentials("http://192.168.1.10:5055/", SeerrAuth.ApiKey("k"))
            val vm = viewModel(LINK, saved)
            vm.settled()
            vm.choose(AddressChoice.Other)
            vm.editOther("http://nas:5055")
            sender.accept = CompletableDeferred(false)

            vm.send()

            vm.uiState.first { it is SendAddressUiState.Ready && it.failed }
            assertEquals(emptyList<String>(), memory.remembered("http://192.168.1.10:5055/"))
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
