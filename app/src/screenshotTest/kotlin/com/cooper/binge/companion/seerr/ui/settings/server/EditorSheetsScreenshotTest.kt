package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheetContent
import com.binge.designsystem.component.BingeMultiChoiceSheetContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame
import com.cooper.binge.companion.seerr.ui.users.settings.TextSettingContent

private val REGIONS = listOf("US", "GB", "DE", "JP", "BR")
private val LANGUAGES =
    listOf(ListEntry("en", "English"), ListEntry("es", "Spanish"), ListEntry("ja", "Japanese"), ListEntry("de", "German"))
private val DISPLAY_LANGUAGES = listOf("" to "Default", "en" to "English", "es-MX" to "Español (México)", "ja" to "日本語")

/**
 * The bodies of the settings editors' sheets that are this app's own: a text value or secret, the notes a picker shows when
 * the server cannot send its list, and the lists the region and language pickers build. Modal windows do not capture, so each
 * frame renders the stateless body at a phone's width, and the ones that matter again in the side sheet at the expanded width.
 */
class EditorSheetsScreenshotTest {
    /** A text setting with an explanation that stays in view and an example while the field is empty. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun textEntry() =
        SheetFrame {
            Text(draft = "", hint = "Where the server answers, without a trailing slash.", placeholder = "https://seerr.lan")
        }

    /** A value the row's own check refuses: Done is off and the reason is under the field. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun textEntryRefused() = SheetFrame { Text(draft = "seerr.lan/", problem = "No trailing slash.") }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun textEntryExpanded() =
        SideSheetFrame { Text(draft = "https://seerr.lan", hint = "Where the server answers, without a trailing slash.") }

    /** A secret edits in a masked field with a reveal toggle. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun secretEntry() =
        SheetFrame {
            Text(draft = "k3y-a-rather-long-secret", secret = true, hint = "Found under the server's General settings.")
        }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun secretEntryExpanded() = SideSheetFrame { Text(draft = "k3y-a-rather-long-secret", secret = true) }

    /** The server could not send its regions: try again, or type the two-letter code. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun regionUnavailable() = SheetFrame { RegionUnavailable(value = "GB", onRetry = {}, onUse = {}) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun regionUnavailableExpanded() = SideSheetFrame { RegionUnavailable(value = "GB", onRetry = {}, onUse = {}) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun languagesUnavailable() = SheetFrame { LanguagesUnavailable(value = "en|es", onRetry = {}, onUse = {}) }

    /** The region picker's list: all regions first, then each region with its flag. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun regionChoices() = SheetFrame { RegionChoices() }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun regionChoicesExpanded() = SideSheetFrame { RegionChoices(dragHandle = false) }

    /** The language filter's list: the chosen ones ticked, each marked with its code. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun languageChoices() = SheetFrame { LanguageChoices() }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun languageChoicesExpanded() = SideSheetFrame { LanguageChoices(dragHandle = false) }

    /** The display language's list: the server's default first, each language by the device's name for it. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun displayLanguageChoices() =
        SheetFrame {
            BingeChoiceSheetContent(
                title = "Display language",
                choices = BingeChoiceList.Ready(displayLanguageChoices(DISPLAY_LANGUAGES)),
                selected = "en",
                onSelect = {},
            )
        }
}

@Composable
private fun Text(
    draft: String,
    secret: Boolean = false,
    hint: String? = null,
    placeholder: String? = null,
    problem: String? = null,
) = TextSettingContent(
    title = "Server address",
    draft = draft,
    onDraftChange = {},
    onSubmit = {},
    onCancel = {},
    secret = secret,
    hint = hint,
    placeholder = placeholder,
    problem = problem,
)

@Composable
private fun RegionChoices(dragHandle: Boolean = true) =
    BingeChoiceSheetContent(
        title = "Discover region",
        choices =
            regionChoiceList(
                REGIONS,
                value = "GB",
                allRegions = stringResource(R.string.server_settings_all_regions),
                defaultLabel = null,
            ),
        selected = "GB",
        onSelect = {},
        dragHandle = dragHandle,
    )

@Composable
private fun LanguageChoices(dragHandle: Boolean = true) =
    BingeMultiChoiceSheetContent(
        title = "Original language",
        choices = BingeChoiceList.Ready(languageChoices(LANGUAGES, listOf("en", "ja"))),
        chosen = setOf("en", "ja"),
        onCheckedChange = { _, _ -> },
        clearLabel = stringResource(R.string.server_settings_list_clear),
        onClear = {},
        doneLabel = stringResource(R.string.editor_done),
        onDone = {},
        dragHandle = dragHandle,
    )
