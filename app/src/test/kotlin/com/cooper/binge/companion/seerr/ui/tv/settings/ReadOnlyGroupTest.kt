package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.binge.designsystem.component.ListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadOnlyGroupTest {
    private fun row(label: String) = ListItem(icon = Icons.Filled.Info, label = label, detail = "value")

    @Test
    fun `the page's note rides on the first read-only row and no other`() {
        var note: String? = "Change this on the phone."
        val take = { note.also { note = null } }

        val first = readOnlyGroup("General", listOf(row("A"), row("B")), take)
        val second = readOnlyGroup("Services", listOf(row("C")), take)

        assertEquals(listOf("Change this on the phone.", null), first.rows.map { it.note })
        assertNull(second.rows.single().note)
    }

    @Test
    fun `a group with no rows leaves the note for the next group that has one`() {
        var note: String? = "Change this on the phone."
        val take = { note.also { note = null } }

        readOnlyGroup("General", emptyList(), take)
        val next = readOnlyGroup("Services", listOf(row("C")), take)

        assertEquals("Change this on the phone.", next.rows.single().note)
    }
}
