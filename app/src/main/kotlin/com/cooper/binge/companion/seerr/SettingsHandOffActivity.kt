package com.cooper.binge.companion.seerr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.cooper.binge.companion.seerr.flags.FeatureFlags
import com.cooper.binge.companion.seerr.flags.appFeatureFlags

/**
 * The Activity behind the host's "manage" affordance. Binge starts it for a result it discards, so
 * all this does is admit its caller and open the hub.
 *
 * It is not an intent filter on [MainActivity] because that one is `singleTask`: launched into its own
 * task, it is not the caller's activity for a result, so `callingPackage` arrives null and the policy
 * below would refuse every host.
 */
class SettingsHandOffActivity : ComponentActivity() {
    /** The graph's flags; a test sets its own, since its Application is not the Hilt one. */
    internal var featureFlags: () -> FeatureFlags = { appFeatureFlags() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (bingeHandOffPolicy(featureFlags()).permits(callingPackage)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
