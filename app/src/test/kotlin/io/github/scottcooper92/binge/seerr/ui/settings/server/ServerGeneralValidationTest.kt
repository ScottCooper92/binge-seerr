package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerGeneralValidationTest {
    private val complete =
        ServerGeneralSettings(
            applicationTitle = "Seerr",
            applicationUrl = "https://requests.home.lan",
            locale = "en",
            discoverRegion = "GB",
            streamingRegion = "GB",
            originalLanguage = "en|ja",
        )

    @Test
    fun `a filled-in page and a blank one both have no issue`() {
        assertTrue(complete.issues().isEmpty())
        assertTrue(ServerGeneralSettings().issues().isEmpty())
    }

    @Test
    fun `a bad application url is an application issue with the web address message`() {
        val issue = complete.copy(applicationUrl = "requests.home.lan").issues().single()

        assertEquals(ServerGeneralSections.APPLICATION, issue.section)
        assertEquals(ServerGeneralFields.APPLICATION_URL, issue.field)
        assertEquals(EditorIssueKind.Invalid, issue.kind)
        assertEquals(R.string.editor_error_web_url, issue.messageRes)
    }

    @Test
    fun `codes of the wrong shape are discover issues, in the order the fields read`() {
        val wrong = complete.copy(originalLanguage = "?", streamingRegion = "Britain", discoverRegion = "UK1", locale = "english")

        assertEquals(
            listOf(
                ServerGeneralFields.LOCALE,
                ServerGeneralFields.DISCOVER_REGION,
                ServerGeneralFields.STREAMING_REGION,
                ServerGeneralFields.ORIGINAL_LANGUAGE,
            ),
            wrong.issues().map { it.field },
        )
        assertTrue(wrong.issues().all { it.section == ServerGeneralSections.DISCOVER && it.kind == EditorIssueKind.Invalid })
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            listOf(
                complete,
                ServerGeneralSettings(),
                complete.copy(applicationUrl = "ftp://x"),
                complete.copy(locale = "english"),
                complete.copy(discoverRegion = "Britain"),
                complete.copy(streamingRegion = null),
                complete.copy(streamingRegion = "Britain"),
                complete.copy(originalLanguage = "japanese"),
            )

        drafts.forEach { draft -> assertEquals("$draft", draft.valid, draft.issues().isEmpty()) }
    }
}
