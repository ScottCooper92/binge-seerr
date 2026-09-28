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
 * App-authored context attached to whatever crash report follows — scoped to breadcrumbs
 * ([log]/[key]), not manual exception recording: Crashlytics already captures the crash itself, and
 * recording it a second time here would double-report it. A no-op wherever Firebase isn't wired up.
 */
interface CrashBreadcrumbs {
    /** A short, fixed-shape note on what was happening, e.g. "approving request". No ids or free text. */
    fun log(message: String)

    /** A key/value pair attached to whatever crash follows, until the next call with the same [name]. */
    fun key(
        name: String,
        value: String,
    )
}

/** Records nothing — the default for a call site (a test, a preview) with no telemetry wired in. */
object NoOpCrashBreadcrumbs : CrashBreadcrumbs {
    override fun log(message: String) = Unit

    override fun key(
        name: String,
        value: String,
    ) = Unit
}

/** [CrashBreadcrumbs] over Crashlytics directly: breadcrumbs are cheap and always recorded, independent of the collection switch. */
class FirebaseCrashBreadcrumbs
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CrashBreadcrumbs {
        override fun log(message: String) {
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseCrashlytics.getInstance().log(message)
        }

        override fun key(
            name: String,
            value: String,
        ) {
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseCrashlytics.getInstance().setCustomKey(name, value)
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
