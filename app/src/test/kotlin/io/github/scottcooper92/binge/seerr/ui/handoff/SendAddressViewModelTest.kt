package io.github.scottcooper92.binge.seerr.ui.handoff

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.handoff.AddressSender
import io.github.scottcooper92.binge.seerr.handoff.AddressSource
import io.github.scottcooper92.binge.seerr.handoff.DataStoreHandOffAddressMemory
import io.github.scottcooper92.binge.seerr.handoff.HAND_OFF_SESSION_MODE
import io.github.scottcooper92.binge.seerr.handoff.HandOffCredentials
import io.github.scottcooper92.binge.seerr.handoff.HandOffKey
import io.github.scottcooper92.binge.seerr.handoff.HandOffStatus
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffLinks
import io.github.scottcooper92.binge.seerr.handoff.TvHandOffTarget
import io.github.scottcooper92.binge.seerr.handoff.TvSignInClient
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TOKEN = "k7m2pqx4"
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
        val sealed = mutableListOf<String?>()
        var accept = CompletableDeferred(true)

        override suspend fun send(
            target: TvHandOffTarget,
            address: String,
            sealed: String?,
        ): Boolean {
            sent += target to address
            this.sealed += sealed
            return accept.await()
        }
    }

    private val sender = RecordingSender()

    /** Answers with [statuses] in turn, the last one for ever; records what it was asked to send. */
    private class FakeTv : TvSignInClient {
        var statuses: MutableList<HandOffStatus?> = mutableListOf(HandOffStatus(HandOffStatus.WAITING))
        val sentCredentials = mutableListOf<HandOffCredentials>()
        var accept = true
        var attempt = 1

        override suspend fun status(target: TvHandOffTarget): HandOffStatus? =
            if (statuses.size >
                1
            ) {
                statuses.removeAt(0)
            } else {
                statuses.first()
            }

        override suspend fun send(
            target: TvHandOffTarget,
            credentials: HandOffCredentials,
        ): Int? {
            sentCredentials += credentials
            return if (accept) attempt else null
        }
    }

    private val tv = FakeTv()

    private val memory = DataStoreHandOffAddressMemory(InMemoryDataStore())

    private suspend fun TestScope.viewModel(
        link: String?,
        saved: SeerrCredentials? = null,
        applicationUrl: String? = null,
        scanned: Boolean = false,
        // A keyed link asks for the TV's PIN first; most tests are about what comes after, so they get past it here.
        enterPin: Boolean = true,
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
                tv = tv,
                applicationUrl = { applicationUrl },
                memory = memory,
                dispatcher = mainDispatcherRule.dispatcher,
                link = link,
                scanned = scanned,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        if (enterPin) TvHandOffLinks.parse(link)?.key?.let { vm.enterPin(it.pin()) }
        // Cleared when the test body ends, before the virtual clock is run out: a poll left running would turn it for ever.
        backgroundScope.launch {
            try {
                awaitCancellation()
            } finally {
                viewModels.clear()
            }
        }
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
            assertEquals("http://seerr.lan:5055/", ready.address)
            assertTrue(ready.suggestions.isEmpty())
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
    fun `the field starts with a local Application URL, and the phone's own address is a suggestion`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("https://seerr.example.com/", SeerrAuth.ApiKey("k")), "http://192.168.1.10:5055")

            val ready = vm.settled() as SendAddressUiState.Ready
            assertEquals("http://192.168.1.10:5055/", ready.address)
            assertEquals(listOf(AddressSource.Connected), ready.suggestions.map { it.source })
            assertTrue(sender.sent.isEmpty())
        }

    @Test
    fun `an Application URL that is the phone's own address is not offered twice`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("http://192.168.1.10:5055/", SeerrAuth.ApiKey("k")), "HTTP://192.168.1.10:5055")

            val ready = vm.settled() as SendAddressUiState.Ready
            assertEquals(1, ready.candidates.size)
            assertTrue(ready.suggestions.isEmpty())
        }

    @Test
    fun `editing the field re-reads it, and a suggestion fills it`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("http://100.101.102.103:5055/", SeerrAuth.ApiKey("k")), "https://seerr.example.com")
            val ready = vm.settled() as SendAddressUiState.Ready
            assertEquals("https://seerr.example.com/", ready.address)
            assertFalse(ready.isNotLocal)

            ready.suggestions.single().let { vm.editAddress(it.address) }
            val picked = vm.uiState.value as SendAddressUiState.Ready
            assertEquals("http://100.101.102.103:5055/", picked.address)
            assertTrue(picked.isNotLocal)
            assertEquals(listOf(AddressSource.ApplicationUrl), picked.suggestions.map { it.source })

            vm.editAddress("http://192.168.1.10:5055")
            val local = vm.uiState.value as SendAddressUiState.Ready
            assertFalse(local.isNotLocal)
            assertFalse(local.isInvalid)
            assertEquals(2, local.suggestions.size)
        }

    @Test
    fun `an entry that is not an address is flagged and cannot be sent, and a blank one cannot either`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("http://192.168.1.10:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()

            vm.editAddress("http://:5055")
            val invalid = vm.uiState.value as SendAddressUiState.Ready
            assertTrue(invalid.isInvalid)
            assertFalse(invalid.canSend)
            vm.send()

            vm.editAddress(" ")
            val blank = vm.uiState.value as SendAddressUiState.Ready
            assertFalse(blank.isInvalid)
            assertFalse(blank.canSend)
            vm.send()

            assertTrue(sender.sent.isEmpty())
        }

    @Test
    fun `a typed address is sent only on the tap, and comes first next time`() =
        runTest {
            val saved = SeerrCredentials("http://100.101.102.103:5055/", SeerrAuth.ApiKey("k"))
            val vm = viewModel(LINK, saved)
            vm.settled()

            vm.editAddress("HTTP://192.168.1.10:5055")
            assertTrue(sender.sent.isEmpty())
            vm.send()

            vm.uiState.first { it is SendAddressUiState.Sent }
            assertEquals("http://192.168.1.10:5055/", sender.sent.single().second)
            assertEquals(listOf("http://192.168.1.10:5055/"), memory.remembered("http://100.101.102.103:5055/"))

            val next = viewModel(LINK, saved).settled() as SendAddressUiState.Ready
            assertEquals("http://192.168.1.10:5055/", next.address)
            assertEquals(AddressSource.Remembered, next.candidates.first().source)
            assertEquals(listOf(AddressSource.Connected), next.suggestions.map { it.source })
        }

    @Test
    fun `the server's own addresses are not remembered, and neither is one the TV refused`() =
        runTest {
            val saved = SeerrCredentials("http://192.168.1.10:5055/", SeerrAuth.ApiKey("k"))
            val vm = viewModel(LINK, saved, applicationUrl = "https://seerr.example.com")
            vm.settled()
            vm.send()
            vm.uiState.first { it is SendAddressUiState.Sent }

            val again = viewModel(LINK, saved, applicationUrl = "https://seerr.example.com")
            again.settled()
            again.editAddress("https://seerr.example.com/")
            again.send()
            again.uiState.first { it is SendAddressUiState.Sent }

            val refused = viewModel(LINK, saved)
            refused.settled()
            refused.editAddress("http://nas:5055")
            sender.accept = CompletableDeferred(false)
            refused.send()
            refused.uiState.first { it is SendAddressUiState.Ready && it.failed }

            assertEquals(emptyList<String>(), memory.remembered("http://192.168.1.10:5055/"))
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }

    /** What a scanned code adds to the link: the key that seals credentials. */
    private val scannedLink = "$LINK&k=${HandOffKey.generate().encoded()}"

    @Test
    fun `a typed address and a link without a key end at Sent, as before`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()

            vm.send()

            assertEquals(SendAddressUiState.Sent("192.168.86.53"), vm.uiState.first { it is SendAddressUiState.Sent })
        }

    @Test
    fun `a scanned code takes a phone with no server to the address field, empty`() =
        runTest {
            val ready = viewModel(scannedLink).settled() as SendAddressUiState.Ready

            assertEquals("", ready.address)
            assertFalse(ready.canSend)
            assertTrue(ready.suggestions.isEmpty())
        }

    @Test
    fun `after the address the sheet follows the TV to its sign-in form, and sends what is typed, sealed by the client`() =
        runTest {
            tv.statuses =
                mutableListOf(
                    HandOffStatus(HandOffStatus.CHECKING),
                    HandOffStatus(HandOffStatus.SIGN_IN, "Living room", listOf("Local", "Jellyfin")),
                )
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()

            vm.send()
            val form = vm.uiState.first { it is SendAddressUiState.SigningIn && it.step is SignInStep.Form }
            val step = (form as SendAddressUiState.SigningIn).step as SignInStep.Form
            assertEquals(listOf(SeerrSignInMode.Local, SeerrSignInMode.Jellyfin), step.modes)
            assertEquals("Living room", step.server)
            assertFalse(step.canSend)

            vm.editSignIn { copy(email = "ana@example.com", password = "correct horse") }
            vm.sendSignIn()

            assertEquals(
                listOf(HandOffCredentials(mode = "Local", email = "ana@example.com", password = "correct horse")),
                tv.sentCredentials,
            )
        }

    @Test
    fun `with the switch on, the phone's session goes with the address, sealed for this TV, and is marked shared`() =
        runTest {
            val key = HandOffKey.generate()
            // Waiting when the sheet opens; checking once the address is in.
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.WAITING), HandOffStatus(HandOffStatus.CHECKING))
            val vm =
                viewModel(
                    "$LINK&k=${key.encoded()}",
                    SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.Session("s1d", 4)),
                    scanned = true,
                )
            val ready = vm.settled() as SendAddressUiState.Ready
            assertTrue("the offer is there for a user session", ready.signIn != null)
            assertFalse("and off until the user turns it on", ready.signInChosen)

            vm.chooseSignIn(true)
            vm.send()
            val step = (vm.uiState.first { it is SendAddressUiState.SigningIn } as SendAddressUiState.SigningIn).step

            assertEquals(SignInStep.Session(awaiting = 1), step)
            val opened = key.open(checkNotNull(sender.sealed.single()), context = TOKEN)?.decodeToString()
            assertEquals(
                HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = "s1d"),
                Json.decodeFromString<HandOffCredentials>(opened!!),
            )
            assertEquals(null, key.open(sender.sealed.single()!!, context = "another1"))
        }

    @Test
    fun `a link a web page could have fired never offers the session, however it is dressed`() =
        runTest {
            val saved = SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.Session("s1d", 4))
            val ready = viewModel(scannedLink, saved).settled() as SendAddressUiState.Ready
            assertEquals(null, ready.signIn)

            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.SIGN_IN, "Living room", listOf("Local")))
            val carried = viewModel(scannedLink, saved)
            val form =
                carried.uiState.first {
                    it is SendAddressUiState.SigningIn && it.step is SignInStep.Form
                } as SendAddressUiState.SigningIn
            assertEquals(null, (form.step as SignInStep.Form).sessionOffer)
            carried.sendSession()
            assertTrue("and sendSession is a no-op", tv.sentCredentials.isEmpty())
        }

    @Test
    fun `with the switch off, or an API key connection, nothing of the session is sent or offered`() =
        runTest {
            val key = HandOffKey.generate()
            val link = "$LINK&k=${key.encoded()}"
            val off = viewModel(link, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.Session("s1d", 4)), scanned = true)
            off.settled()
            off.send()
            off.uiState.first { it is SendAddressUiState.SigningIn }
            assertEquals(listOf<String?>(null), sender.sealed)

            val keyed =
                viewModel(
                    link,
                    SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")),
                    scanned = true,
                ).settled() as SendAddressUiState.Ready
            assertEquals(null, keyed.signIn)
        }

    @Test
    fun `a TV already on its sign-in step skips the address and offers the session as one tap`() =
        runTest {
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.SIGN_IN, "Living room", listOf("Local")))
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.Session("s1d", 4)), scanned = true)

            val form = vm.uiState.first { it is SendAddressUiState.SigningIn && it.step is SignInStep.Form } as SendAddressUiState.SigningIn
            assertTrue((form.step as SignInStep.Form).sessionOffer != null)
            assertTrue("no address is sent to a TV past that step", sender.sent.isEmpty())

            vm.sendSession()
            vm.uiState.first { ((it as? SendAddressUiState.SigningIn)?.step as? SignInStep.Form)?.awaiting != null }
            assertEquals(listOf(HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = "s1d")), tv.sentCredentials)
        }

    @Test
    fun `the TV saying no brings the form back with its fields, and saying yes ends on Connected`() =
        runTest {
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.SIGN_IN, "Home", listOf("Jellyfin")))
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()
            vm.send()
            vm.uiState.first { it is SendAddressUiState.SigningIn && it.step is SignInStep.Form }
            vm.editSignIn { copy(username = "ana", password = "wrong") }

            vm.sendSignIn()
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.SIGN_IN, "Home", listOf("Jellyfin"), failed = true, attempt = 1))
            val rejected = vm.uiState.first { it is SendAddressUiState.SigningIn && (it.step as? SignInStep.Form)?.rejected == true }
            val step = (rejected as SendAddressUiState.SigningIn).step as SignInStep.Form
            assertEquals("ana", step.form.username)
            assertFalse(step.isSending)

            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.CONNECTED))
            assertEquals(
                SignInStep.Connected,
                (
                    vm.uiState.first {
                        (it as? SendAddressUiState.SigningIn)?.step == SignInStep.Connected
                    } as SendAddressUiState.SigningIn
                ).step,
            )
        }

    /** #912: a public plain-HTTP address puts the TV on its opt-in, which is not the TV checking the server. */
    @Test
    fun `a TV asking its user about plain HTTP says so, and the session that went with the address comes back after`() =
        runTest {
            val key = HandOffKey.generate()
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.WAITING), HandOffStatus(HandOffStatus.CONFIRM))
            val vm =
                viewModel(
                    "$LINK&k=${key.encoded()}",
                    SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.Session("s1d", 4)),
                    scanned = true,
                )
            vm.settled()
            vm.chooseSignIn(true)
            vm.send()

            val confirming = SignInStep.ConfirmOnTv(resume = SignInStep.Session(awaiting = 1))
            vm.uiState.first { (it as? SendAddressUiState.SigningIn)?.step == confirming }

            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.CHECKING))
            vm.uiState.first { (it as? SendAddressUiState.SigningIn)?.step == SignInStep.Session(awaiting = 1) }
        }

    @Test
    fun `a code scanned while the TV waits on its user carries on there, and sends no address`() =
        runTest {
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.CONFIRM))
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")), scanned = true)

            vm.uiState.first { (it as? SendAddressUiState.SigningIn)?.step == SignInStep.ConfirmOnTv() }
            assertTrue(sender.sent.isEmpty())

            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.CHECKING))
            vm.uiState.first { (it as? SendAddressUiState.SigningIn)?.step == SignInStep.Waiting }
        }

    /** Back on the TV from its sign-in step: the form goes too, rather than send to a step the TV has left (#804). */
    @Test
    fun `the TV going back to its address step takes the sheet back to waiting, and its sign-in step brings the form again`() =
        runTest {
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.SIGN_IN, "Home", listOf("Jellyfin")))
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()
            vm.send()
            vm.uiState.first { it is SendAddressUiState.SigningIn && it.step is SignInStep.Form }

            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.WAITING))
            vm.uiState.first { (it as? SendAddressUiState.SigningIn)?.step == SignInStep.Waiting }

            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.CHECKING), HandOffStatus(HandOffStatus.SIGN_IN, "Den", listOf("Local")))
            val again = vm.uiState.first { ((it as? SendAddressUiState.SigningIn)?.step as? SignInStep.Form)?.server == "Den" }
            assertEquals(listOf(SeerrSignInMode.Local), ((again as SendAddressUiState.SigningIn).step as SignInStep.Form).modes)
            assertTrue(tv.sentCredentials.isEmpty())
        }

    @Test
    fun `a failed left over from the last attempt is not the refusal of a retry until the TV has counted the retry`() =
        runTest {
            val stale = HandOffStatus(HandOffStatus.SIGN_IN, "Home", listOf("Jellyfin"), failed = true, attempt = 1)
            tv.statuses = mutableListOf(stale)
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()
            vm.send()
            vm.uiState.first { it is SendAddressUiState.SigningIn && it.step is SignInStep.Form }
            vm.editSignIn { copy(username = "ana", password = "wrong") }
            vm.sendSignIn()
            vm.uiState.first { ((it as? SendAddressUiState.SigningIn)?.step as? SignInStep.Form)?.rejected == true }

            // The retry is the TV's second attempt; its status still says `failed`, as of the first.
            tv.attempt = 2
            vm.editSignIn { copy(password = "right") }
            vm.sendSignIn()
            vm.uiState.first { ((it as? SendAddressUiState.SigningIn)?.step as? SignInStep.Form)?.awaiting == 2 }
            advanceTimeBy(5_000)
            runCurrent()
            val sending = (vm.uiState.value as SendAddressUiState.SigningIn).step as SignInStep.Form
            assertTrue(sending.isSending)
            assertFalse(sending.rejected)

            tv.statuses = mutableListOf(stale.copy(attempt = 2))
            vm.uiState.first { ((it as? SendAddressUiState.SigningIn)?.step as? SignInStep.Form)?.rejected == true }
        }

    @Test
    fun `a TV that could not use the address returns the sheet to the field, and one that goes quiet is lost`() =
        runTest {
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.FAILED))
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()
            vm.send()
            val back = vm.uiState.first { it is SendAddressUiState.Ready && it.failed } as SendAddressUiState.Ready
            assertEquals("http://seerr.lan:5055/", back.address)

            tv.statuses = mutableListOf(null)
            vm.send()
            assertEquals(
                SignInStep.Lost,
                (
                    vm.uiState.first {
                        (it as? SendAddressUiState.SigningIn)?.step == SignInStep.Lost
                    } as SendAddressUiState.SigningIn
                ).step,
            )
        }

    @Test
    fun `a TV that offers only sign-ins finished on its own screen says so, and a refused send shows the form again`() =
        runTest {
            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.SIGN_IN, "Home", listOf("Plex", "QuickConnect")))
            val onTv = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            onTv.settled()
            onTv.send()
            assertEquals(
                SignInStep.OnTv("Home"),
                (
                    onTv.uiState.first {
                        (it as? SendAddressUiState.SigningIn)?.step is SignInStep.OnTv
                    } as SendAddressUiState.SigningIn
                ).step,
            )

            tv.statuses = mutableListOf(HandOffStatus(HandOffStatus.SIGN_IN, "Home", listOf("Local")))
            tv.accept = false
            val vm = viewModel(scannedLink, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")))
            vm.settled()
            vm.send()
            vm.uiState.first { it is SendAddressUiState.SigningIn && it.step is SignInStep.Form }
            vm.editSignIn { copy(email = "a@b.c", password = "x") }
            vm.sendSignIn()
            assertTrue(
                (
                    (
                        vm.uiState.first {
                            (it as? SendAddressUiState.SigningIn)?.step.let { s ->
                                s is SignInStep.Form && s.rejected
                            }
                        } as SendAddressUiState.SigningIn
                    ).step as SignInStep.Form
                ).rejected,
            )
        }

    @Test
    fun `a keyed link asks for the TV's PIN before anything, and sends nothing until it matches`() =
        runTest {
            val key = HandOffKey.generate()
            val vm =
                viewModel("$LINK&k=${key.encoded()}", SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")), enterPin = false)
            assertEquals(SendAddressUiState.EnterPin(tv = "192.168.86.53"), vm.uiState.value)

            vm.enterPin("12")
            assertEquals("12", (vm.uiState.value as SendAddressUiState.EnterPin).entered)
            val wrongPin = if (key.pin() == "0000") "0001" else "0000"
            vm.enterPin(wrongPin)
            assertEquals(SendAddressUiState.EnterPin(tv = "192.168.86.53", entered = "", wrong = true), vm.uiState.value)
            vm.enterPin("4")
            assertEquals(SendAddressUiState.EnterPin(tv = "192.168.86.53", entered = "4", wrong = false), vm.uiState.value)
            assertTrue("nothing reaches the TV before the PIN", tv.sentCredentials.isEmpty())

            vm.enterPin(key.pin())
            assertTrue(vm.settled() is SendAddressUiState.Ready)
        }

    @Test
    fun `a link without a key has no PIN to ask for`() =
        runTest {
            val vm = viewModel(LINK, SeerrCredentials("http://seerr.lan:5055/", SeerrAuth.ApiKey("k")), enterPin = false)
            assertTrue(vm.settled() is SendAddressUiState.Ready)
        }
}
