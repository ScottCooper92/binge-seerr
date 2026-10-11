package com.cooper.binge.companion.seerr.ui.users

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.ui.users.settings.NotificationType
import com.cooper.binge.companion.seerr.ui.users.settings.icon
import org.junit.Assert.assertEquals
import org.junit.Test

class UserIconsTest {
    @Test
    fun `a permission's movie and series children take the movie and TV glyphs`() {
        val children = ManageablePermission.entries.filter { it.parent != null && it.name.endsWith("Movies") }
        assertEquals(5, children.size)
        children.forEach { assertEquals(it.name, Icons.Filled.Movie, it.icon()) }
        ManageablePermission.entries
            .filter { it.parent != null && it.name.endsWith("Series") }
            .forEach { assertEquals(it.name, Icons.Filled.Tv, it.icon()) }
    }

    @Test
    fun `every permission that is not a media type child has a glyph of its own`() {
        val roots = ManageablePermission.entries.filterNot { it.name.endsWith("Movies") || it.name.endsWith("Series") }
        assertEquals(roots.size, roots.map { it.icon() }.toSet().size)
    }

    @Test
    fun `every notification event has a glyph of its own`() {
        assertEquals(
            NotificationType.entries.size,
            NotificationType.entries
                .map { it.icon() }
                .toSet()
                .size,
        )
    }

    @Test
    fun `every kind of account has a glyph of its own`() {
        assertEquals(
            UserOrigin.entries.size,
            UserOrigin.entries
                .map { it.icon() }
                .toSet()
                .size,
        )
    }
}
