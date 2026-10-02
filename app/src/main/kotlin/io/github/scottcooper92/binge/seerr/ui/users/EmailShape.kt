package io.github.scottcooper92.binge.seerr.ui.users

/**
 * A deliberately lenient email check: one `@`, something on each side, and no whitespace. It catches a
 * typo without refusing an unusual valid address; the server stays the final judge.
 */
internal fun String.isEmailShape(): Boolean {
    val address = trim()
    if (address.any { it.isWhitespace() }) return false
    val parts = address.split('@')
    return parts.size == 2 && parts.all { it.isNotEmpty() }
}
