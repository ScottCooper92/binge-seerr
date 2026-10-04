package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.GeneralFields.EMAIL
import io.github.scottcooper92.binge.seerr.ui.users.settings.GeneralFields.REGION
import io.github.scottcooper92.binge.seerr.ui.users.settings.GeneralFields.TV_QUOTA_DAYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralSettingsValidationTest {
    private val complete =
        GeneralSettings(
            displayName = "Ann",
            email = "ann@home.lan",
            discordId = "123456789012345678",
            locale = "en",
            region = "GB",
            originalLanguage = "ja|ko",
            movieQuotaLimit = "5",
            movieQuotaDays = "7",
        )

    @Test
    fun `a filled-in page and a blank one both have no issue`() {
        assertTrue(complete.issues().isEmpty())
        assertTrue(GeneralSettings().issues().isEmpty())
    }

    @Test
    fun `every issue is a value of the wrong shape, never a missing one`() {
        val wrong =
            complete.copy(
                email = "ann",
                discordId = "ann#1",
                locale = "english",
                region = "Britain",
                originalLanguage = "?",
                tvQuotaDays = "-1",
            )

        assertTrue(wrong.issues().all { it.kind == EditorIssueKind.Invalid })
    }

    @Test
    fun `issues are listed in the order the sections read`() {
        val wrong = complete.copy(tvQuotaDays = "x", region = "Britain", email = "ann")

        assertEquals(listOf(EMAIL, REGION, TV_QUOTA_DAYS), wrong.issues().map { it.field })
        assertEquals(
            listOf(GeneralSections.PROFILE, GeneralSections.DISCOVER, GeneralSections.QUOTAS),
            wrong.issues().map { it.section },
        )
    }

    @Test
    fun `a quota that is not a whole number says so`() {
        assertEquals(
            R.string.editor_error_whole_number,
            complete
                .copy(movieQuotaLimit = "five")
                .issues()
                .single()
                .messageRes,
        )
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            listOf(
                complete,
                GeneralSettings(),
                complete.copy(email = "nope"),
                complete.copy(email = "nope", loadedEmail = "nope"),
                complete.copy(discordId = "abc"),
                complete.copy(locale = "english"),
                complete.copy(region = "Britain"),
                complete.copy(originalLanguage = "japanese"),
                complete.copy(movieQuotaLimit = "-1"),
                complete.copy(movieQuotaDays = "a"),
                complete.copy(tvQuotaLimit = "1.5"),
                complete.copy(tvQuotaDays = " "),
            )

        drafts.forEach { draft -> assertEquals("$draft", draft.valid, draft.issues().isEmpty()) }
    }
}
