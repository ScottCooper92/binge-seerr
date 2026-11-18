package com.cooper.binge.companion.seerr.discover.request

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun items(vararg open: Boolean) = open.mapIndexed { index, isOpen -> SelectableItem(index + 1, isOpen) }

class ItemSelectionTest {
    @Test
    fun `everything open is ticked before the user touches it when there is no quota`() {
        val selection = ItemSelection(items(true, true, false)).preselected()

        assertEquals(listOf(1, 2), selection.requestIds)
        assertTrue(selection.allOpenPicked)
    }

    @Test
    fun `everything open is ticked when the quota covers it and nothing when it cannot`() {
        assertEquals(listOf(1, 2), ItemSelection(items(true, true), limit = 2).preselected().requestIds)
        assertEquals(emptyList<Int>(), ItemSelection(items(true, true, true), limit = 2).preselected().requestIds)
    }

    @Test
    fun `a closed or unknown item is never ticked`() {
        val selection = ItemSelection(items(true, false)).toggle(2).toggle(9)

        assertEquals(emptyList<Int>(), selection.requestIds)
    }

    @Test
    fun `the quota caps how many can be ticked one at a time`() {
        val selection = ItemSelection(items(true, true, true), limit = 2).toggle(1).toggle(2).toggle(3)

        assertEquals(listOf(1, 2), selection.requestIds)
        assertFalse(selection.canPickMore)
    }

    @Test
    fun `all flips every open item and back, and does nothing when the quota cannot take them all`() {
        val all = ItemSelection(items(true, true, false)).toggleAll()
        assertEquals(listOf(1, 2), all.requestIds)
        assertEquals(emptyList<Int>(), all.toggleAll().requestIds)

        val capped = ItemSelection(items(true, true, true), limit = 2)
        assertEquals(capped, capped.toggleAll())
        assertFalse(capped.canPickAll)
    }

    @Test
    fun `nothing ticked cannot be sent`() {
        assertFalse(ItemSelection(items(true)).canSubmit)
        assertTrue(ItemSelection(items(true)).toggle(1).canSubmit)
    }
}
