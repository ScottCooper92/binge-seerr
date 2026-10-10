package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A requester and a full user record are named by one rule on the app's own screens (#700). */
class UserDisplayStringTest {
    @Test
    fun `the display name comes first, then the username`() {
        assertEquals("Ana", SeerrRequestUserDto(displayName = "Ana", username = "ana", email = "ana@example.com").displayString())
        assertEquals("ana", SeerrRequestUserDto(displayName = " ", username = "ana", email = "ana@example.com").displayString())
    }

    @Test
    fun `an email is the last resort, cut to its local part`() {
        assertEquals("ana", SeerrRequestUserDto(email = "ana@example.com").displayString())
        assertNull(SeerrRequestUserDto(email = "@example.com").displayString())
        assertNull(SeerrRequestUserDto().displayString())
    }

    /** #1216: Seerr fills `displayName` with the email for a user created by email, so a name can be the address. */
    @Test
    fun `a display name that is the email is masked like the email`() {
        assertEquals("ana", SeerrRequestUserDto(displayName = "ana@example.com", email = "ana@example.com").displayString())
        assertEquals("ana", SeerrRequestUserDto(displayName = "Ana@Example.com", email = "ana@example.com").displayString())
        assertEquals("ana", SeerrRequestUserDto(displayName = "ana@example.com").displayString())
        // A real username behind it still wins over the masked address.
        assertEquals(
            "ana_r",
            SeerrRequestUserDto(displayName = "ana@example.com", username = "ana_r", email = "ana@example.com").displayString(),
        )
        assertEquals("ana", SeerrUserDto(id = 1, displayName = "ana@example.com", email = "ana@example.com").displayString())
    }

    @Test
    fun `a full user record is named the same way as a requester`() {
        val cases =
            listOf(
                Triple("Ana", "ana", "ana@example.com"),
                Triple("", "ana", "ana@example.com"),
                Triple(null, null, "ana@example.com"),
                Triple("ana@example.com", null, "ana@example.com"),
                Triple(null, null, null),
            )
        cases.forEach { (displayName, username, email) ->
            assertEquals(
                SeerrRequestUserDto(displayName = displayName, username = username, email = email).displayString(),
                SeerrUserDto(id = 1, displayName = displayName, username = username, email = email).displayString(),
            )
        }
    }
}
