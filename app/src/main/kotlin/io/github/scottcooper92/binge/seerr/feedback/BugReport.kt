package io.github.scottcooper92.binge.seerr.feedback

import android.os.Build
import io.github.scottcooper92.binge.seerr.BuildConfig
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Where a bug in this app is reported: a new issue on this repository, through the form in
 * `.github/ISSUE_TEMPLATE/bug.yml`. A fork points this at its own repository.
 */
const val BUG_REPORT_REPOSITORY = "https://github.com/ScottCooper92/binge-seerr"

private const val BUG_TEMPLATE = "bug.yml"

/**
 * The facts a report is prefilled with. Only the app and the device: never the server's address or
 * the user's name, because the issue is public.
 */
data class DeviceReport(
    val appVersion: String,
    val device: String,
    val androidVersion: String,
)

/**
 * A new-issue link on [repository] that opens the bug form with [report] filled in. The keys are the
 * form's field ids: an issue form ignores a `body=`, so each field is prefilled by its own.
 */
fun bugReportUrl(
    report: DeviceReport,
    repository: String = BUG_REPORT_REPOSITORY,
): String {
    val query =
        listOf(
            "template" to BUG_TEMPLATE,
            "app-version" to report.appVersion,
            "device" to report.device,
            "android-version" to report.androidVersion,
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}" }
    return "${repository.trimEnd('/')}/issues/new?$query"
}

/** This install's [DeviceReport], and the link it makes. */
class BugReportLinks
    @Inject
    constructor() {
        fun url(): String =
            bugReportUrl(
                DeviceReport(
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                ),
            )
    }
