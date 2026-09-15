package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.ui.users.settings.PasswordSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val SHORT = "a".repeat(PasswordSettings.MIN_PASSWORD_LENGTH - 1)
private val LONG_ENOUGH = "a".repeat(PasswordSettings.MIN_PASSWORD_LENGTH)

/**
 * When the create-account form says the password is wrong. Create being greyed out says something is
 * wrong without saying which of the three fields it is, so the field has to answer for itself.
 */
class CreateUserDraftTest {
    @Test
    fun `an untouched password is quiet rather than red`() {
        assertFalse(CreateUserDraft().passwordTooShort)
    }

    @Test
    fun `a password under the minimum is an error once something has been typed`() {
        assertTrue(CreateUserDraft(password = SHORT).passwordTooShort)
    }

    @Test
    fun `a password at the minimum is not`() {
        assertFalse(CreateUserDraft(password = LONG_ENOUGH).passwordTooShort)
    }

    @Test
    fun `asking the server to generate one takes the field away, so it cannot be wrong`() {
        assertFalse(CreateUserDraft(password = SHORT, generatePassword = true).passwordTooShort)
    }

    @Test
    fun `the rule matches the one the submit button reads`() {
        val short = CreateUserDraft(email = "ada@example.com", username = "Ada", password = SHORT)

        assertFalse(short.valid)
        assertTrue(short.passwordTooShort)
        assertTrue(short.copy(password = LONG_ENOUGH).valid)
    }
}
