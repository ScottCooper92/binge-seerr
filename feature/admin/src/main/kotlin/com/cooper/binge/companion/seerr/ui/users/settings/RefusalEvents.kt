package com.cooper.binge.companion.seerr.ui.users.settings

import com.cooper.homelab.core.presentation.editor.RefusalNotice

/**
 * The event for a save the server refused. The shared library's [RefusalNotice] decides how it is said: a [RefusalNotice.Dialog]
 * keeps the server's reason, so the page reads it in a dialog, and anything else leaves it out, so the page shows a snackbar.
 */
fun RefusalNotice.toRefusedEvent(failure: Throwable): EditorEvent.Failed {
    val event = EditorEvent.Failed.from(failure)
    return if (this is RefusalNotice.Dialog) event else event.copy(detail = null)
}
