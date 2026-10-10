package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.ui.users.settings.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

/** One rule names a person's role wherever the app tags it. */
class UserRoleTest {
    @Test
    fun `the first account is the owner, whatever its permissions`() {
        assertEquals(UserRole.Owner, userRole(OWNER_USER_ID, isAdmin = true))
        assertEquals(UserRole.Owner, userRole(OWNER_USER_ID, isAdmin = false))
    }

    @Test
    fun `any other administrator is an admin, and anyone else a user`() {
        assertEquals(UserRole.Admin, userRole(id = 2, isAdmin = true))
        assertEquals(UserRole.User, userRole(id = 2, isAdmin = false))
        assertEquals(UserRole.User, userRole(id = null, isAdmin = false))
    }
}
