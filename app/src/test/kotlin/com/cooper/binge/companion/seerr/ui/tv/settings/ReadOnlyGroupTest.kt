package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.tv.template.TvPaneGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadOnlyGroupTest {
    private fun row(label: String) = ListItem(icon = Icons.Filled.Info, label = label, detail = "value")

    @Test
    fun `a read-only row carries no note of its own`() {
        val group = readOnlyGroup("General", listOf(row("A"), row("B")))

        assertEquals(listOf(null, null), group.rows.map { it.note })
        assertNull(group.note)
    }

    @Test
    fun `every server group page carries the note, since each is a page of its own`() {
        val groups =
            listOf(
                readOnlyGroup("General", listOf(row("A"))),
                readOnlyGroup("Services", listOf(row("B"))),
                readOnlyGroup("System", listOf(row("C"))),
            )

        val noted = groups.carryingNote("Change this on the phone.")

        assertEquals(List(groups.size) { "Change this on the phone." }, noted.map(TvPaneGroup::note))
        assertEquals(groups.map { it.rows }, noted.map { it.rows })
    }

    @Test
    fun `no groups leaves no note to place`() {
        assertEquals(emptyList<TvPaneGroup>(), emptyList<TvPaneGroup>().carryingNote("Change this on the phone."))
    }
}
