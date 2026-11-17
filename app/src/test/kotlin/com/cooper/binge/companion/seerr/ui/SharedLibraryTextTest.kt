package com.cooper.binge.companion.seerr.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.homelab.core.foundation.UiText
import com.cooper.homelab.core.presentation.editor.EditorText
import com.cooper.homelab.core.uiandroid.resolve
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A text key from the shared library resolves against this app's merged resources, under `context.packageName`. Robolectric runs
 * with the namespace as the package, so it cannot prove the debug build's `.debug` application id suffix: that is shown by the
 * debug APK's resource table, whose package is `com.cooper.binge.companion.seerr.debug`, the same as the installed package name.
 */
@RunWith(RobolectricTestRunner::class)
class SharedLibraryTextTest {
    @Test
    fun `a shared library text resolves in the app`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("Not saved", UiText.keyed(EditorText.NotSaved).resolve(context))
    }
}
