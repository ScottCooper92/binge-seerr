package io.github.scottcooper92.binge.seerr.seerr

/**
 * The requester as REQUEST v1 lets this companion name them to the host: a display name or username, never an
 * email address or any part of one (binge-companions#121). Seerr's `displayName` is not always a name: the server
 * fills it with `username || plexUsername || jellyfinUsername || email`, so for a user created by email it is the
 * email itself. That is why this filters: a candidate equal to [SeerrRequestUserDto.email], or containing an `@` when
 * the payload carries no email to compare with, is dropped. Unlike [displayString], which the app's own screens use,
 * there is no email fallback; with no name left the field stays empty, which the contract reads as "doesn't say".
 */
internal fun SeerrRequestUserDto.contractName(): String? =
    listOfNotNull(displayName, username).firstOrNull {
        it.isNotBlank() && '@' !in it && !it.equals(email, ignoreCase = true)
    }

/**
 * For the app's own screens: email is a last resort, masked to its local part. The host never gets this; it gets
 * [contractName], which has no email fallback at all.
 */
internal fun SeerrRequestUserDto.displayString(): String? = screenName(displayName, username, email)

/** The same name for a full user record, so a requester and an account are named by one rule (#700). */
internal fun SeerrUserDto.displayString(): String? = screenName(displayName, username, email)

private fun screenName(
    displayName: String?,
    username: String?,
    email: String?,
): String? =
    listOfNotNull(displayName, username).firstOrNull { it.isNotBlank() }
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
