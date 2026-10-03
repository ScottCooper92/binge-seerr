package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrValidationTest {
    private val complete =
        DvrForm.blank(ServiceType.Radarr).copy(
            name = "Radarr",
            host = "radarr.lan",
            apiKey = "key",
            profileId = 4,
            rootFolder = "/movies",
        )

    private fun DvrForm.fields(choicesLoaded: Boolean = true) = issues(choicesLoaded).map { it.field }

    @Test
    fun `a complete draft has no issue`() {
        assertTrue(complete.issues(choicesLoaded = true).isEmpty())
    }

    @Test
    fun `a blank new instance lists every required field in section order`() {
        val blank = DvrForm.blank(ServiceType.Radarr)

        assertEquals(
            listOf(DvrFields.NAME, DvrFields.HOST, DvrFields.API_KEY, DvrSections.DESTINATION),
            blank.fields(choicesLoaded = false),
        )
    }

    @Test
    fun `once a test has answered the destination names the picker that is empty`() {
        val blank = DvrForm.blank(ServiceType.Radarr)

        assertEquals(
            listOf(DvrFields.NAME, DvrFields.HOST, DvrFields.API_KEY, DvrFields.PROFILE, DvrFields.ROOT_FOLDER),
            blank.fields(choicesLoaded = true),
        )
    }

    @Test
    fun `a port that is not a number is invalid, not missing`() {
        val issue = complete.copy(port = "78x8").issues(true).single()

        assertEquals(DvrFields.PORT, issue.field)
        assertEquals(EditorIssueKind.Invalid, issue.kind)
        assertEquals(R.string.editor_error_port, issue.messageRes)
    }

    @Test
    fun `a blank port is missing`() {
        assertEquals(
            EditorIssueKind.Missing,
            complete
                .copy(port = "")
                .issues(true)
                .single()
                .kind,
        )
    }

    @Test
    fun `a bad external url is an advanced issue`() {
        val issue = complete.copy(externalUrl = "ftp://x").issues(true).single()

        assertEquals(DvrSections.ADVANCED, issue.section)
        assertEquals(EditorIssueKind.Invalid, issue.kind)
    }

    @Test
    fun `a saved instance whose test failed still saves, as valid says`() {
        assertTrue(complete.valid)
        assertTrue(complete.issues(choicesLoaded = false).isEmpty())
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            listOf(
                complete,
                complete.copy(name = " "),
                complete.copy(host = ""),
                complete.copy(port = "0"),
                complete.copy(port = "65536"),
                complete.copy(apiKey = ""),
                complete.copy(profileId = null),
                complete.copy(rootFolder = " "),
                complete.copy(externalUrl = "nope"),
                complete.copy(externalUrl = "https://seerr.example"),
                DvrForm.blank(ServiceType.Sonarr),
            )

        drafts.forEach { draft ->
            assertEquals("$draft", draft.valid, draft.issues(choicesLoaded = true).isEmpty())
        }
    }
}
