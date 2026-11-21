package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.component.BingeNavSuiteItem
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Settings item in the nav pill is the account's avatar once the account is known, and the Settings icon until then. */
@RunWith(RobolectricTestRunner::class)
class SeerrTabItemsTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val ana = HubAccount(id = 7, name = "Ana", isAdmin = false, avatarUrl = "https://seerr.example.org/avatar.png")

    private fun items(account: HubAccount?): List<BingeNavSuiteItem> {
        var items = emptyList<BingeNavSuiteItem>()
        rule.setContent { items = seerrTabItems(account) }
        rule.waitForIdle()
        return items
    }

    private fun List<BingeNavSuiteItem>.settings() = single { it.key == SeerrTab.Settings }

    @Test
    fun `with an account the Settings item shows its photo and name, large`() {
        val settings = items(ana).settings()

        assertEquals("Ana", settings.avatarName)
        assertEquals("https://seerr.example.org/avatar.png", settings.avatarUrl)
        assertTrue(settings.largeAvatar)
    }

    @Test
    fun `an account with no photo is its initials, still an avatar`() {
        val settings = items(ana.copy(avatarUrl = null)).settings()

        assertEquals("Ana", settings.avatarName)
        assertNull(settings.avatarUrl)
    }

    @Test
    fun `before the account is known the item is the Settings icon`() {
        val settings = items(null).settings()

        assertNull(settings.avatarName)
        assertFalse(settings.largeAvatar)
    }

    @Test
    fun `no other tab takes the avatar and the label stays Settings`() {
        val all = items(ana)

        assertTrue(all.filter { it.key != SeerrTab.Settings }.all { it.avatarName == null })
        assertEquals("Settings", all.settings().label)
    }
}
