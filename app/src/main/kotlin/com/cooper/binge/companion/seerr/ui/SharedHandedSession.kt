package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.handoff.HAND_OFF_SESSION_MODE
import com.cooper.binge.companion.seerr.handoff.HandOffCredentials
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectViewModel
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the shared setup does with what a phone sends the television: its address (alone, or with the session it signed in with), the
 * credentials it typed, and the wait for the local-network permission that a session may sit through. It reads and writes the setup's
 * own state ([app]) and drives the connect [machine]; [inspect] is the setup's own read of the address in hand.
 *
 * A phone's address fills the field and waits for the TV's user (#1084). A session sealed for it proves the sender had the code's key
 * and is bound to that address (#1029), so it goes straight to the read.
 */
internal class SharedHandedSession(
    private val app: MutableStateFlow<SharedSetupApp>,
    private val machine: ConnectViewModel<SetupServer, Unit>,
    private val connection: SeerrConnection,
    private val analytics: Analytics,
    private val crashBreadcrumbs: CrashBreadcrumbs,
    private val inspect: () -> Unit,
) {
    /** The address a phone sent, with its session when it had one: the field takes it, and a session goes straight to the read. */
    fun onAddress(
        address: String,
        session: String?,
    ) {
        machine.editAddress(address)
        app.update {
            it.copy(
                handOff = null,
                received = true,
                handedSession = session,
                awaitingPermission = false,
                handOffRejected = false,
            )
        }
        if (session != null) inspect()
    }

    /**
     * With the phone's session, straight in: the sign-in form is shown only if the server turns it down. The server decides:
     * a session it does not answer to is dropped, never kept, and the sign-in step comes up with a line saying why.
     */
    suspend fun adopt(server: ProbedServer<SetupServer>): Boolean {
        val session = app.value.handedSession
        if (session == null) return false
        app.update { it.copy(handedSession = null, awaitingPermission = false) }
        // A server that is not set up has been read but cannot judge a session, so it is not kept for a later read (#1994).
        if (!server.setUp) return false
        val adopted = connection.adoptHandedSession(server.preview.baseUrl, session, analytics, crashBreadcrumbs)
        if (!adopted) app.update { it.copy(handOffRejected = true) }
        return adopted
    }

    /**
     * Credentials a phone app sealed for this TV, already opened by the listener. They go into the form and through connect()
     * exactly as if typed. Taken only on the sign-in step, for a mode that has fields to fill, and never while an attempt runs.
     */
    fun onCredentials(credentials: HandOffCredentials) {
        val current = machine.draft.value
        val server = current.server?.takeIf { !current.busy }
        val form = server?.let { credentials.toSignInForm(it.preview.modes) }
        val handedSession = credentials.mode == HAND_OFF_SESSION_MODE && credentials.session.isNotEmpty()
        if (server != null && handedSession) {
            app.update {
                it.copy(
                    handedSession = credentials.session,
                    awaitingPermission = false,
                    notice = null,
                    handOffRejected = false,
                )
            }
            machine.editAddress(current.address)
            inspect()
        } else if (form != null) {
            app.update { it.copy(attempts = it.attempts + 1, notice = null, handOffRejected = false) }
            form.mode.formMode()?.let(machine::selectMode)
            machine.editForm {
                listOf(
                    FIELD_API_KEY to form.apiKey,
                    FIELD_USERNAME to form.username,
                    FIELD_EMAIL to form.email,
                    FIELD_PASSWORD to form.password,
                ).fold(this) { acc, (id, value) -> if (value.isEmpty()) acc else acc.with(id, value) }
            }
            machine.connect()
        } else {
            app.update { it.copy(attempts = it.attempts + 1) }
        }
    }

    /**
     * A phone's session waits for the read it came with. A read that failed ends it for good, so it is never tried against
     * another address, except when the failure is the local-network permission: the session then waits for the grant's read,
     * and is adopted once that read has answered (#2428). Only that read is adopted here: any other read takes the session
     * through `inspect(then)`, so this does not count or adopt it a second time.
     */
    fun watch(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ) {
        scope.launch(dispatcher) {
            machine.draft.collect { draft ->
                if (app.value.handedSession == null) {
                    if (app.value.awaitingPermission) app.update { it.copy(awaitingPermission = false) }
                    return@collect
                }
                val server = draft.server
                val waiting = app.value.awaitingPermission
                when {
                    draft.error == ConnectError.Failed(ServerFailure.LocalNetworkDenied) ->
                        if (!waiting) app.update { it.copy(awaitingPermission = true) }
                    draft.error != null -> app.update { it.copy(handedSession = null, awaitingPermission = false) }
                    waiting && server != null && !draft.busy -> {
                        app.update { it.copy(awaitingPermission = false) }
                        if (server.setUp) {
                            app.update { it.copy(attempts = it.attempts + 1) }
                            adopt(server)
                        } else {
                            // A server that is not set up cannot judge the session: it is dropped, not kept (#1994).
                            app.update { it.copy(handedSession = null) }
                        }
                    }
                }
            }
        }
    }
}
