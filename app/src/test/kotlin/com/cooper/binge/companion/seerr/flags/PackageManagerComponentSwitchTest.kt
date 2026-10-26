package com.cooper.binge.companion.seerr.flags

import android.content.ComponentName
import android.content.pm.PackageManager
import com.cooper.binge.companion.seerr.service.SeerrCompanionService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PackageManagerComponentSwitchTest {
    private val context = RuntimeEnvironment.getApplication()
    private val service = ComponentName(context, SeerrCompanionService::class.java)
    private val switch = PackageManagerComponentSwitch(context, SeerrCompanionService::class.java)

    @Test
    fun `on enables the Service and off returns it to the manifest's own state`() {
        switch.setEnabled(true)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, context.packageManager.getComponentEnabledSetting(service))

        switch.setEnabled(false)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(service))
    }
}
