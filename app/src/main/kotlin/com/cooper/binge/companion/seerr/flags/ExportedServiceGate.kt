package com.cooper.binge.companion.seerr.flags

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
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
 * Switches one manifest component with the package manager, without killing the process. On enables it. Off goes back
 * to the manifest's own value, which for the Service is disabled, rather than forcing a state of its own.
 */
class PackageManagerComponentSwitch(
    private val context: Context,
    private val component: Class<*>,
) : ComponentSwitch {
    override fun setEnabled(enabled: Boolean) {
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        // A refused change leaves the component as it was, which the Service's own flag checks still cover.
        attempt {
            context.packageManager.setComponentEnabledSetting(ComponentName(context, component), state, PackageManager.DONT_KILL_APP)
        }
    }
}

/**
 * Keeps the exported Service out of a host's discovery unless `request_companions_enabled` is on. The REQUEST contract's
 * core (Handshake, SubmitRequest, GetStatus) cannot be served otherwise, and a service that cannot serve it must not
 * export the REQUEST action. The manifest declares the component disabled, so a fresh install is off; this enables it
 * when the flag is on. The one component also carries NOTIFICATIONS, which goes with it; forwarding is never active
 * while the flag is off, so nothing is lost.
 *
 * Applied from the cached value as the app starts and again whenever the flag changes. A host that had already bound
 * when the flag turned off is covered by the Service's own flag checks.
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
