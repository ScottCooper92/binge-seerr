package io.github.scottcooper92.binge.seerr.ui.users.settings

/** A Discord user ID is a snowflake: ASCII digits only. A paste of a username or a mention fails here, not at the server. */
internal fun String.isDiscordIdShape(): Boolean = trim().all { it in '0'..'9' }
