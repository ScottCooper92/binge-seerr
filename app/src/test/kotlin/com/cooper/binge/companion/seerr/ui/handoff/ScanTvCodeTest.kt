package com.cooper.binge.companion.seerr.ui.handoff

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** #1944: the scan starts only where Play Services' scanner activity resolves; elsewhere ML Kit's own activity would crash. */
@RunWith(RobolectricTestRunner::class)
class ScanTvCodeTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val scan = Intent("com.google.android.gms.mlkit.ACTION_SCAN_BARCODE").setPackage("com.google.android.gms")

    @Test
    fun `a device with no scanner activity cannot open it`() {
        assertFalse(app.packageManager.canOpenScanner())
    }

    @Test
    fun `a device whose Play Services answers the scan action can`() {
        val info =
            ActivityInfo().apply {
                packageName = "com.google.android.gms"
                name = "com.google.mlkit.vision.codescanner.internal.Scanner"
            }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            ComponentName(info.packageName, info.name),
            android.content.IntentFilter(scan.action),
        )

        assertTrue(app.packageManager.canOpenScanner())
    }
}
