package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile

/** What an address answered before any credential was typed: the sign-in form is built from this. */
data class SeerrServerPreview(
    val baseUrl: String,
    val profile: SeerrServerProfile,
    val backdropUrls: List<String>,
)

/** The entered address cannot be parsed as a URL even after normalisation. */
class InvalidServerUrlException : IllegalArgumentException("Invalid Seerr server URL")

/** At-rest encryption of the credentials failed, so nothing was persisted. */
class CredentialsSaveException : IllegalStateException("Failed to store the Seerr credentials")

/** The login succeeded but the server set no session cookie, so there is nothing to keep. */
class NoSessionCookieException : IllegalStateException("Seerr login returned no session cookie")

/** No server is connected: the code the contract's `UNAUTHENTICATED` is mapped from. */
class NotConnectedException : IllegalStateException("No Seerr server is connected")

/** The address answered, but not as a Seerr server: neither profile call came back as one. */
class NotSeerrServerException(
    cause: Throwable,
) : IllegalStateException("The address did not answer as a Seerr server", cause)
