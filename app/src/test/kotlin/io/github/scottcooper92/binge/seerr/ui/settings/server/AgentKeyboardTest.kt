package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each agent option's sheet asks for the keyboard its value needs. */
class AgentKeyboardTest {
    @Test
    fun `a port gets a number pad`() {
        assertEquals(KeyboardType.Number, AgentOption.EmailSmtpPort.keyboard().keyboardType)
    }

    @Test
    fun `an address gets the uri keyboard with nothing to autocorrect it`() {
        val keyboard = AgentOption.DiscordWebhookUrl.keyboard()

        assertEquals(KeyboardType.Uri, keyboard.keyboardType)
        assertFalse(keyboard.autoCorrectEnabled ?: true)
    }

    @Test
    fun `a username the server matches exactly is not autocorrected, and plain text is`() {
        assertFalse(AgentOption.EmailAuthUser.keyboard().autoCorrectEnabled ?: true)
        assertTrue(AgentOption.DiscordBotUsername.keyboard().autoCorrectEnabled ?: false)
    }
}
