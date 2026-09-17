package io.github.scottcooper92.binge.seerr.ui.requests

import com.binge.designsystem.component.InfoValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [linkedOrPlain] is what stands between a name and `GET /user/{id}`: the server refuses that
 * call for anyone but the caller themselves or a `MANAGE_USERS` admin (#345's review), so a link
 * only renders where one of those is true.
 */
class LinkedOrPlainTest {
    @Test
    fun `a viewer's own id links even without MANAGE_USERS`() {
        var opened: Int? = null
        val value = linkedOrPlain("Scott", id = 7, viewerId = 7, canManageUsers = false, onOpenUser = { opened = it })

        val link = value as? InfoValue.Link
        assertEquals("Scott", link?.text)
        link?.onClick?.invoke()
        assertEquals(7, opened)
    }

    @Test
    fun `MANAGE_USERS links any other id`() {
        var opened: Int? = null
        val value = linkedOrPlain("Admin", id = 9, viewerId = 7, canManageUsers = true, onOpenUser = { opened = it })

        val link = value as? InfoValue.Link
        assertEquals("Admin", link?.text)
        link?.onClick?.invoke()
        assertEquals(9, opened)
    }

    @Test
    fun `neither the viewer's own id nor MANAGE_USERS falls back to plain text`() {
        val value = linkedOrPlain("Admin", id = 9, viewerId = 7, canManageUsers = false, onOpenUser = {})

        assertEquals(InfoValue.Plain("Admin"), value)
        assertTrue(value is InfoValue.Plain)
    }

    @Test
    fun `no id at all is always plain text`() {
        val value = linkedOrPlain("Unknown", id = null, viewerId = 7, canManageUsers = true, onOpenUser = {})

        assertEquals(InfoValue.Plain("Unknown"), value)
    }
}
