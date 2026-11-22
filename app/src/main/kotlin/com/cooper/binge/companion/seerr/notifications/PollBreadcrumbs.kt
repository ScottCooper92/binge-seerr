package com.cooper.binge.companion.seerr.notifications

import com.cooper.homelab.core.notifications.PollBreadcrumbs
import com.cooper.homelab.core.notifications.classNameOnly
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs

/** The packages whose failures the poll's reports are about: this app's and the shared library's. */
private val POLL_OWN_PACKAGES = listOf("com.cooper.binge.companion.seerr.", "com.cooper.homelab.core.")

/**
 * These breadcrumbs for the poll's scheduling failures. Only a failure that is the app's own is forwarded, and with its
 * message replaced by its class name; a cancellation, or a device that cannot run WorkManager, is not a bug here.
 */
internal fun CrashBreadcrumbs.forPoll(): CrashBreadcrumbs = PollBreadcrumbs(this, POLL_OWN_PACKAGES, ::classNameOnly)
