package com.cooper.binge.companion.seerr.feedback

import android.os.Build
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.homelab.core.feedback.BugReport
import com.cooper.homelab.core.feedback.bugReportUrl
import javax.inject.Inject

/**
 * Where a bug in this app is reported: a new issue on the public app-feedback repository, through its
 * `seerr-bug.yml` form, which labels the issue for this app.
 */
const val BUG_REPORT_REPOSITORY = "https://github.com/ScottCooper92/app-feedback"

private const val BUG_TEMPLATE = "seerr-bug.yml"

/**
 * The bug form with the facts a report is prefilled with. Only the app and the device: never the server's
 * address or the user's name, because the issue is public. The keys are the form's field ids.
 */
internal fun seerrBugReport(
    appVersion: String,
    device: String,
    androidVersion: String,
): BugReport =
    BugReport(
        template = BUG_TEMPLATE,
        fields =
            mapOf(
                "app-version" to appVersion,
                "device" to device,
                "android-version" to androidVersion,
            ),
    )

/** This install's report, and the link it makes. */
class BugReportLinks
    @Inject
    constructor() {
        fun url(): String =
            bugReportUrl(
                BUG_REPORT_REPOSITORY,
                seerrBugReport(
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                ),
            )
    }
