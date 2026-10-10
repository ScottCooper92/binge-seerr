package io.github.scottcooper92.binge.seerr.ui.state

import com.binge.designsystem.ErrorKind
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import org.junit.Assert.assertEquals
import org.junit.Test

/** How a failure reads on the error screen: the design system's kind picks the icon, and the words are this app's (#1054). */
class SeerrErrorCopyTest {
    @Test
    fun `each failure picks the icon of the kind it is`() {
        val kinds = SeerrError.entries.associateWith { it.toErrorKind() }

        assertEquals(
            mapOf(
                SeerrError.NotConnected to ErrorKind.Generic,
                SeerrError.Unauthorized to ErrorKind.Auth,
                SeerrError.Forbidden to ErrorKind.Forbidden,
                SeerrError.Quota to ErrorKind.RateLimited,
                SeerrError.NotFound to ErrorKind.NotFound,
                SeerrError.Unreachable to ErrorKind.Network,
                SeerrError.Server to ErrorKind.Server,
                SeerrError.Rejected to ErrorKind.Generic,
                SeerrError.Unknown to ErrorKind.Generic,
            ),
            kinds,
        )
    }

    @Test
    fun `the quota, a missing title and a rejection each say what happened`() {
        assertEquals(R.string.state_error_quota_title, SeerrError.Quota.titleRes())
        assertEquals(R.string.state_error_quota_message, SeerrError.Quota.messageRes())
        assertEquals(R.string.state_error_not_found_title, SeerrError.NotFound.titleRes())
        assertEquals(R.string.state_error_not_found_message, SeerrError.NotFound.messageRes())
        assertEquals(R.string.state_error_rejected_title, SeerrError.Rejected.titleRes())
        assertEquals(R.string.state_error_rejected_message, SeerrError.Rejected.messageRes())
    }

    /** A copy-pasted arm would show one failure's words for another; every failure has its own. */
    @Test
    fun `no two failures share a title or a message`() {
        val titles = SeerrError.entries.map { it.titleRes() }
        val messages = SeerrError.entries.map { it.messageRes() }

        assertEquals(SeerrError.entries.size, titles.toSet().size)
        assertEquals(SeerrError.entries.size, messages.toSet().size)
    }
}
