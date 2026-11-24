package com.cooper.binge.companion.seerr.ui.requests

/**
 * The actions whose progress shows on the control that started them. [closesSheet] is whether the actions sheet goes when
 * one lands: Decline and Remove end the sheet's business with the request, Mark as leaves it open on the new status.
 */
enum class RequestAction(
    val closesSheet: Boolean,
) {
    Decline(closesSheet = true),
    Remove(closesSheet = true),
    MarkAs(closesSheet = false),
}

/**
 * [action] is running, or, with [failed], did not go through and may be tried again. [attempt] numbers the tries, so a sheet
 * opened after a failure can tell that failure from one of its own. [is4k] names the instance a Mark as is acting on.
 */
data class ActionProgress(
    val action: RequestAction,
    val failed: Boolean = false,
    val attempt: Int = 0,
    val is4k: Boolean = false,
)
