package io.github.scottcooper92.binge.seerr.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where crash reports go, as far as the switch is concerned: on or off. */
fun interface CrashCollection {
    fun setEnabled(enabled: Boolean)
}

/**
 * Crashlytics' collection switch. A build without this app's `google-services.json` has no Firebase
 * app to report into, so there it does nothing rather than fail.
 */
class FirebaseCrashCollection
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CrashCollection {
        override fun setEnabled(enabled: Boolean) {
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
        }
    }

/**
 * Holds crash collection to the user's crash-reporting preference, for the life of the app. A debug
 * build never collects, whatever the preference says. No user id is ever set: a crash is reported
 * against the install, not a person.
 */
@Singleton
class CrashReportingSwitch internal constructor(
    private val collection: CrashCollection,
    private val prefs: TelemetryPrefs,
    private val scope: CoroutineScope,
    private val isDebugBuild: Boolean,
) {
    @Inject
    constructor(
        collection: CrashCollection,
        prefs: TelemetryPrefs,
        @ApplicationScope scope: CoroutineScope,
    ) : this(collection, prefs, scope, BuildConfig.DEBUG)

    fun start() {
        scope.launch {
            prefs.crashReportingEnabled.distinctUntilChanged().collect { enabled ->
                collection.setEnabled(!isDebugBuild && enabled)
            }
        }
    }
}
