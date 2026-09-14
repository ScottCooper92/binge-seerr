package io.github.scottcooper92.binge.seerr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The Activity behind the host's "manage" affordance. Binge starts it for a result and discards
 * the result; it takes no extras and answers none, so all it does is admit its caller and open the
 * hub.
 *
 * It is its own Activity rather than an intent filter on [MainActivity] because that one is
 * `singleTask`. An Activity that launches into a task of its own is not the caller's activity for
 * a result, so `callingPackage` arrives null and the policy below would refuse every host — the
 * check would read as present and admit nobody.
 */
class SettingsHandOffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (bingeHandOffPolicy().permits(callingPackage)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
