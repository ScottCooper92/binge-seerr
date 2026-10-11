package com.cooper.binge.companion.seerr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled._4k
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.icon
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.icon
import com.cooper.binge.companion.seerr.ui.users.UserSort
import com.cooper.binge.companion.seerr.ui.users.icon
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-choice glyphs the choice sheets show (#1391, #1392). A choice sheet whose rows share one glyph says nothing
 * the labels don't, so each list's glyphs are distinct.
 */
class ChoiceIconsTest {
    @Test
    fun `every sort and every availability has a glyph of its own`() {
        listOf(
            RequestSort.entries.map { it.icon() },
            IssueSort.entries.map { it.icon() },
            UserSort.entries.map { it.icon() },
            MediaStatusChoice.entries.map { it.icon() },
        ).forEach { icons -> assertEquals(icons.size, icons.toSet().size) }
    }

    @Test
    fun `a media instance shows the 4K badge, else the title's kind`() {
        fun instance(is4k: Boolean) = MediaInstance(is4k = is4k, status = null, serviceUrl = null, mediaServerUrl = null, watch = null)

        assertEquals(Icons.Filled.Movie, instance(is4k = false).icon(isTv = false))
        assertEquals(Icons.Filled.Tv, instance(is4k = false).icon(isTv = true))
        assertEquals(Icons.Filled._4k, instance(is4k = true).icon(isTv = true))
    }

    @Test
    fun `the destination fields each have their own glyph`() {
        val icons = listOf(DestinationIcons.server, DestinationIcons.profile, DestinationIcons.rootFolder)

        assertEquals(icons.size, icons.toSet().size)
    }
}
