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
 * For the app's own screens: email is a last resort, masked to its local part. A `displayName` or `username` that is
 * the email (Seerr fills `displayName` with it for a user created by email), or holds an `@`, is the email too, and is
 * masked the same way (#1216). The host never gets this; it gets [contractName], which has no email fallback at all.
 */
internal fun SeerrRequestUserDto.displayString(): String? = screenName(displayName, username, email)

/**
 * A picture to load: the server's address for it, where that is a web address. Seerr also sends a path on itself for
 * an uploaded one, which needs the session to fetch and is left to the initials.
 */
internal fun String?.toAvatarUrl(): String? = this?.takeIf { it.startsWith("http") }

/** The same name for a full user record, so a requester and an account are named by one rule (#700). */
internal fun SeerrUserDto.displayString(): String? = screenName(displayName, username, email)

private fun screenName(
    displayName: String?,
    username: String?,
    email: String?,
): String? =
    listOfNotNull(displayName, username).filter { it.isNotBlank() }.let { names ->
        names.firstOrNull { '@' !in it && !it.equals(email, ignoreCase = true) }
            ?: (email ?: names.firstOrNull())?.substringBefore('@')?.takeIf { it.isNotBlank() }
    }
