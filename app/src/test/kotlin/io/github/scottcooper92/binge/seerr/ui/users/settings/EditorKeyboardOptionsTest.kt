package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorKeyboardOptionsTest {
    @Test
    fun `prose starts sentences capitalised`() {
        val options = editorKeyboardOptions(secret = false, keyboardType = KeyboardType.Text, autoCorrect = true)
        assertEquals(KeyboardCapitalization.Sentences, options.capitalization)
        assertEquals(KeyboardType.Text, options.keyboardType)
    }

    @Test
    fun `an identifier is left as typed`() {
        val options = editorKeyboardOptions(secret = false, keyboardType = KeyboardType.Text, autoCorrect = false)
        assertEquals(KeyboardCapitalization.None, options.capitalization)
    }

    @Test
    fun `urls emails and numbers are not capitalised`() {
        listOf(KeyboardType.Uri, KeyboardType.Email, KeyboardType.Number).forEach { type ->
            val options = editorKeyboardOptions(secret = false, keyboardType = type, autoCorrect = true)
            assertEquals(KeyboardCapitalization.None, options.capitalization)
            assertEquals(type, options.keyboardType)
        }
    }

    @Test
    fun `a secret is a password keyboard and never capitalised`() {
        val options = editorKeyboardOptions(secret = true, keyboardType = KeyboardType.Text, autoCorrect = true)
        assertEquals(KeyboardType.Password, options.keyboardType)
        assertEquals(KeyboardCapitalization.None, options.capitalization)
    }
}
