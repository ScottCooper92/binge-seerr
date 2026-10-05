package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaServerValidationTest {
    private val plex = MediaServerForm(kind = MediaServerKind.Plex, host = "plex.lan", port = "32400", externalUrl = "https://app.plex.tv/")
    private val jellyfin =
        MediaServerForm(kind = MediaServerKind.Jellyfin, host = "jf.lan", port = "8096", forgotPasswordUrl = "", apiKey = "k")

    @Test
    fun `a complete form has no issue`() {
        assertTrue(plex.issues().isEmpty())
        assertTrue(jellyfin.issues().isEmpty())
    }

    @Test
    fun `a blank host and port are missing from the connection`() {
        val issues = plex.copy(host = "", port = "").issues()

        assertEquals(listOf(MediaServerFields.HOST, MediaServerFields.PORT), issues.map { it.field })
        assertTrue(issues.all { it.kind == EditorIssueKind.Missing && it.section == MediaServerSections.CONNECTION })
    }

    @Test
    fun `a port out of range is invalid`() {
        assertEquals(
            R.string.editor_error_port,
            plex
                .copy(port = "0")
                .issues()
                .single()
                .messageRes,
        )
    }

    @Test
    fun `a jellyfin link with a trailing slash says no slash, a plex one with no scheme does not`() {
        val jellyfinIssue = jellyfin.copy(externalUrl = "https://jf.example.com/").issues().single()
        val plexIssue = plex.copy(externalUrl = "app.plex.tv").issues().single()

        assertEquals(MediaServerSections.LINKS, jellyfinIssue.section)
        assertEquals(R.string.editor_error_web_url_no_slash, jellyfinIssue.messageRes)
        assertEquals(R.string.editor_error_web_url, plexIssue.messageRes)
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            listOf(
                plex,
                jellyfin,
                plex.copy(host = " "),
                plex.copy(port = ""),
                plex.copy(port = "99999"),
                plex.copy(externalUrl = "app.plex.tv"),
                plex.copy(externalUrl = ""),
                jellyfin.copy(externalUrl = "https://jf.example.com/"),
                jellyfin.copy(forgotPasswordUrl = "https://jf.example.com/forgot/"),
                jellyfin.copy(forgotPasswordUrl = "nope"),
                jellyfin.copy(forgotPasswordUrl = null),
            )

        drafts.forEach { draft -> assertEquals("$draft", draft.valid, draft.issues().isEmpty()) }
    }
}
