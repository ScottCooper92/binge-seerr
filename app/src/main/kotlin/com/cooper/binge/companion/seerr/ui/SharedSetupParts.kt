package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.auth.NotSeerrServerException
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrLoginRequest
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.Authenticator
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.FieldKind
import com.cooper.homelab.core.presentation.connect.FieldSpec
import com.cooper.homelab.core.presentation.connect.NotThisServerException
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.ServerProbe
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.presentation.connect.SignInMode
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs

/** The form's field ids. A phone's credentials name the same four, so the television hand-off fills the form by them. */
internal const val FIELD_API_KEY = "apiKey"
internal const val FIELD_USERNAME = "username"
internal const val FIELD_EMAIL = "email"
internal const val FIELD_PASSWORD = "password"

/** A sign-in of Seerr's that is typed into a form, as the shared machine sees it. [mode] is its [id]. */
internal class SeerrMode(
    val mode: SeerrSignInMode,
    override val fields: List<FieldSpec>,
) : SignInMode {
    override val id: String = mode.name
}

private val FORM_MODES: Map<SeerrSignInMode, SeerrMode> =
    listOf(
        SeerrMode(SeerrSignInMode.ApiKey, listOf(FieldSpec(FIELD_API_KEY, FieldKind.Secret))),
        SeerrMode(SeerrSignInMode.Local, listOf(FieldSpec(FIELD_EMAIL, FieldKind.Email), FieldSpec(FIELD_PASSWORD, FieldKind.Secret))),
        SeerrMode(
            SeerrSignInMode.Jellyfin,
            listOf(FieldSpec(FIELD_USERNAME, FieldKind.Username), FieldSpec(FIELD_PASSWORD, FieldKind.Secret)),
        ),
        SeerrMode(
            SeerrSignInMode.Emby,
            listOf(FieldSpec(FIELD_USERNAME, FieldKind.Username), FieldSpec(FIELD_PASSWORD, FieldKind.Secret)),
        ),
    ).associateBy { it.mode }

/** This mode as a form of fields, or null for Plex and Quick Connect, which finish in another app and stay on the old path. */
internal fun SeerrSignInMode.formMode(): SeerrMode? = FORM_MODES[this]

/** The modes of [this] server that the shared machine can run, in the order the form offers them. */
internal fun SetupServer.formModes(): List<SeerrSignInMode> = modes.filter { it.formMode() != null }

/** Reads the server at one candidate address and shapes the answer for the machine. A server that answers but is not Seerr is its own case. */
internal class SeerrServerProbe(
    private val connection: SeerrConnection,
) : ServerProbe<SetupServer> {
    override suspend fun probe(address: ServerAddress): ProbedServer<SetupServer> {
        val preview =
            connection.inspectExact(address.url).getOrElse { failure ->
                throw if (failure is NotSeerrServerException) NotThisServerException() else failure
            }
        val server = preview.toSetupServer().let { it.copy(modes = it.formModes()) }
        return ProbedServer(
            address = address,
            name = server.title,
            setUp = server.setUp,
            modes = server.modes.mapNotNull { it.formMode() },
            preview = server,
        )
    }
}

/**
 * One authenticator per typed mode. [SeerrConnection] stores the session itself, in one atomic save, so the machine's session
 * value is [Unit] and its sink has nothing left to do. The key, the email and the username are trimmed here; the password is not.
 */
internal fun seerrAuthenticators(
    connection: SeerrConnection,
    crashBreadcrumbs: CrashBreadcrumbs,
): Map<String, Authenticator<SetupServer, Unit>> {
    fun typed(
        mode: SeerrSignInMode,
        signIn: suspend (baseUrl: String, form: SignInForm) -> Result<*>,
    ) = mode.name to
        Authenticator<SetupServer, Unit> { server, form ->
            crashBreadcrumbs.log("signing in via $mode")
            signIn(server.preview.baseUrl, form).getOrThrow()
            Unit
        }

    fun jellyfin(mode: SeerrSignInMode) =
        typed(mode) { baseUrl, form ->
            connection.logIn(baseUrl, SeerrLoginRequest.Jellyfin(form.value(FIELD_USERNAME).trim(), form.value(FIELD_PASSWORD)))
        }
    return mapOf(
        typed(SeerrSignInMode.ApiKey) { baseUrl, form -> connection.connect(baseUrl, SeerrAuth.ApiKey(form.value(FIELD_API_KEY).trim())) },
        typed(SeerrSignInMode.Local) { baseUrl, form ->
            connection.logIn(baseUrl, SeerrLoginRequest.Local(form.value(FIELD_EMAIL).trim(), form.value(FIELD_PASSWORD)))
        },
        jellyfin(SeerrSignInMode.Jellyfin),
        jellyfin(SeerrSignInMode.Emby),
    )
}

/**
 * What a failure is called on the setup form. The same rule as the old path: a refused sign-in is [SetupError.Rejected], an
 * unreachable server, the local-network permission and a client certificate are their own, and the rest is [SetupError.Unknown].
 */
internal fun ConnectError?.toSetupError(): SetupError? =
    when (this) {
        null -> null
        ConnectError.InvalidAddress -> SetupError.InvalidUrl
        ConnectError.NotThisServer -> SetupError.NotSeerr
        ConnectError.LinkExpired -> SetupError.LinkExpired
        ConnectError.LinkNotOffered -> SetupError.Unknown
        is ConnectError.Failed ->
            when (failure) {
                ServerFailure.SessionRejected, ServerFailure.NotPermitted -> SetupError.Rejected
                is ServerFailure.Unreachable -> SetupError.Unreachable
                ServerFailure.LocalNetworkDenied -> SetupError.LocalNetworkDenied
                ServerFailure.ClientCertificateNeeded -> SetupError.ClientCertificateNeeded
                else -> SetupError.Unknown
            }
    }
