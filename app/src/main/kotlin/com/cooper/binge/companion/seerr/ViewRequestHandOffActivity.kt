package com.cooper.binge.companion.seerr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.binge.companion.sdk.CompanionManifest
import com.cooper.binge.companion.seerr.ui.DeepLinks

/**
 * The Activity behind the host's "open this request" hand-off. Binge starts it for a result with the
 * `RequestInfo.id` this app answered, which here is the Seerr request's number. It admits its caller
 * and opens [MainActivity] on that request's page, over the requests list and the hub, so Back walks
 * the way it would by hand. An id that is not one of ours opens the requests list instead.
 *
 * Its own Activity rather than a filter on [MainActivity], for the reason [SettingsHandOffActivity] gives.
 */
class ViewRequestHandOffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (bingeHandOffPolicy().permits(callingPackage)) {
            startActivity(Intent(this, MainActivity::class.java).setData(Uri.parse(linkFor(intent.getStringExtra(CompanionManifest.EXTRA_REQUEST_ID)))))
        }
        finish()
    }
}

/** The link a hand-off for [requestId] opens: its request's page, or the requests list when it names none of ours. */
internal fun linkFor(requestId: String?): String =
    requestId
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?.let(DeepLinks::request)
        ?: DeepLinks.requests()
