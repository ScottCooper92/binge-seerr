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
    fun `an email that is not the shape of one blocks Create and is flagged`() {
        listOf("@", "a@", "@b", "a@@b", "a@b@c", "a b@c").forEach { bad ->
            val draft = ready().copy(email = bad)
            assertFalse(bad, draft.valid)
            assertTrue(bad, draft.emailInvalid)
        }
    }

    @Test
    fun `a blank email blocks Create but is not flagged until typed`() {
        val draft = ready().copy(email = "  ")
        assertFalse(draft.valid)
        assertFalse(draft.emailInvalid)
    }

    @Test
    fun `the shortest address and a padded one pass, the same as the profile editor`() {
        assertTrue(ready().copy(email = "a@b").valid)
        assertTrue(ready().copy(email = " ada@example.com ").valid)
        assertFalse(ready().copy(email = "a@b").emailInvalid)
    }

    private fun ready() = CreateUserDraft(email = "ada@example.com", username = "Ada", password = LONG_ENOUGH)

    @Test
    fun `the rule matches the one the submit button reads`() {
        val short = CreateUserDraft(email = "ada@example.com", username = "Ada", password = SHORT)

        assertFalse(short.valid)
        assertTrue(short.passwordTooShort)
        assertTrue(short.copy(password = LONG_ENOUGH).valid)
    }
}
