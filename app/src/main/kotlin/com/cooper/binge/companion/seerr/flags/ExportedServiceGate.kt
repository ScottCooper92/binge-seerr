package com.cooper.binge.companion.seerr.flags

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the exported Service is there for a host to find, as far as [ExportedServiceGate] is concerned. */
fun interface ComponentSwitch {
    fun setEnabled(enabled: Boolean)
}

/**
 * Switches one manifest component on or off with the package manager. On goes back to the manifest's own default
 * rather than forcing the component on, and the process is not killed either way.
 */
class PackageManagerComponentSwitch(
    private val context: Context,
    private val component: Class<*>,
) : ComponentSwitch {
    override fun setEnabled(enabled: Boolean) {
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // A refused change leaves the component as it was, which the Service's own flag checks still cover.
        attempt {
            context.packageManager.setComponentEnabledSetting(ComponentName(context, component), state, PackageManager.DONT_KILL_APP)
        }
    }
}

/**
 * Keeps the exported Service out of a host's discovery while `request_companions_enabled` is off. The REQUEST contract's
 * core (Handshake, SubmitRequest, GetStatus) cannot be served then, and a service that cannot serve it must not export
 * the REQUEST action. The one component also carries NOTIFICATIONS, which goes with it; forwarding is never active
 * while the flag is off, so nothing is lost.
 *
 * Applied from the cached value as the app starts and again whenever the flag changes, so a fetch that turns it on
 * puts the Service back. A host that had already bound is covered by the Service's own flag checks.
 */
class ExportedServiceGate
    @Inject
    constructor(
        private val featureFlags: FeatureFlags,
        private val serviceSwitch: ComponentSwitch,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            scope.launch {
                featureFlags.flags
                    .map { it.requestCompanions }
                    .distinctUntilChanged()
                    .collect(serviceSwitch::setEnabled)
            }
        }
    }
