package com.cooper.binge.companion.seerr.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.cooper.homelab.core.uiandroid.R as CoreR

/**
 * The shake prompt's words come from the shared library now. They are the same words, in both languages, that this app shipped
 * as its own strings, and the label on its confirm button is still this app's own "Report a bug".
 */
@RunWith(RobolectricTestRunner::class)
class ShakePromptWordingTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the prompt reads as it did in English`() {
        assertEquals("Report a bug?", context.getString(CoreR.string.shake_report_title))
        assertEquals(
            "This opens a new issue on this app's GitHub page, with your app version and device filled in. Nothing about your server is included.",
            context.getString(CoreR.string.shake_report_message),
        )
        assertEquals("Report a bug", context.getString(R.string.settings_report_bug))
    }

    @Test
    @Config(qualifiers = "es")
    fun `the prompt reads as it did in Spanish`() {
        assertEquals("¿Informar de un error?", context.getString(CoreR.string.shake_report_title))
        assertEquals(
            "Se abrirá una incidencia nueva en la página de GitHub de esta aplicación, con la versión de la aplicación y el dispositivo ya rellenados. No se incluye nada sobre tu servidor.",
            context.getString(CoreR.string.shake_report_message),
        )
    }
}
