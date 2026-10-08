package io.github.scottcooper92.binge.seerr.ui.users.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web client's rules for the notifications page: shapes for what is there, and values for an agent sent events. */
class NotificationsRulesTest {
    private val pushoverKey = "uQiRzpo4DXghDmr9QzzfQu27cmVRsG"

    @Test
    fun `nothing set is valid`() {
        assertTrue(NotificationSettings().valid)
    }

    @Test
    fun `each discord id is blank or digits, and one is needed while discord is sent events`() {
        val discord = NotificationSettings().update(NotificationAgent.Discord) { it.copy(enabled = true) }
        assertTrue(discord.copy(discordIds = listOf("123", "")).valid)
        assertFalse(discord.copy(discordIds = listOf("123", "ann#1")).valid)

        val sent = discord.update(NotificationAgent.Discord) { it.copy(types = NotificationType.MediaApproved.bit) }
        assertFalse(sent.valid)
        assertTrue(sent.copy(discordIds = listOf("123")).valid)
    }

    @Test
    fun `pushover takes thirty letters and digits, and both keys once one is set and it is sent events`() {
        val one = NotificationSettings().set(AgentField.PushoverUserKey, pushoverKey)
        assertTrue(one.valid)
        assertFalse(one.set(AgentField.PushoverUserKey, "short").valid)

        val sent = one.update(NotificationAgent.Pushover) { it.copy(types = NotificationType.MediaApproved.bit) }
        assertTrue(sent.required(AgentField.PushoverAppToken))
        assertFalse(sent.valid)
        assertTrue(sent.set(AgentField.PushoverAppToken, pushoverKey.reversed()).valid)
    }

    @Test
    fun `a telegram chat id may be negative and its topic is a whole number`() {
        val telegram = NotificationSettings().set(AgentField.TelegramChatId, "-1001234")
        assertTrue(telegram.valid)
        assertFalse(telegram.set(AgentField.TelegramChatId, "@group").valid)
        assertTrue(telegram.set(AgentField.TelegramThreadId, "42").valid)
        assertFalse(telegram.set(AgentField.TelegramThreadId, "-4").valid)
    }

    @Test
    fun `a pgp key is the whole armoured block`() {
        val key = "-----BEGIN PGP PUBLIC KEY BLOCK-----\nmQINBGB\n-----END PGP PUBLIC KEY BLOCK-----"
        assertTrue(NotificationSettings().set(AgentField.PgpKey, key).valid)
        assertFalse(NotificationSettings().set(AgentField.PgpKey, "mQINBGB").valid)
    }

    @Test
    fun `the sound alone does not turn pushover on`() {
        assertFalse(NotificationSettings().set(AgentField.PushoverSound, "bike").isOn(NotificationAgent.Pushover))
    }

    @Test
    fun `every field takes a blank value, which is how a value is removed`() {
        AgentField.entries.forEach { assertTrue(it.name, it.accepts("")) }
    }
}
