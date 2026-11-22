package com.cooper.binge.companion.seerr.ui.users.settings

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.homelab.core.foundation.UiText
import com.cooper.homelab.core.presentation.editor.RefusalNotice
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private const val REASON = "That name is taken"
private const val REJECTED = 400

private fun rejected() =
    HttpException(Response.error<Any>(REJECTED, """{"message":"$REASON"}""".toResponseBody("application/json".toMediaType())))

class RefusalEventsTest {
    @Test
    fun `a dialog notice keeps the server's reason on the event`() {
        val notice = RefusalNotice.Dialog(UiText.Verbatim("Not saved"), UiText.Verbatim(REASON))

        val event = notice.toRefusedEvent(rejected())

        assertEquals(REASON, event.detail)
        assertEquals(SeerrError.Rejected, event.error)
    }

    @Test
    fun `a snackbar notice leaves the reason out and keeps the error`() {
        val notice = RefusalNotice.Snackbar(UiText.Verbatim("Not saved"))

        val event = notice.toRefusedEvent(rejected())

        assertNull(event.detail)
        assertEquals(SeerrError.Rejected, event.error)
    }
}
