package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "Delete files" is a per-instance offer: [MediaRecord.canDeleteFiles] is who may delete at all,
 * but only an instance whose own status implies files exist should actually show the button
 * (#338).
 */
@RunWith(RobolectricTestRunner::class)
class RequestActionsContentDeleteFilesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Every status the server can report, plus no status at all — three of them offer the button. */
    @Test
    fun `the button offers only for a status that implies files exist`() {
        val statuses =
            listOf(
                null,
                SeerrMediaStatusCode.Unknown,
                SeerrMediaStatusCode.Pending,
                SeerrMediaStatusCode.Processing,
                SeerrMediaStatusCode.PartiallyAvailable,
                SeerrMediaStatusCode.Available,
                SeerrMediaStatusCode.Blocklisted,
                SeerrMediaStatusCode.Deleted,
            )
        setContent(instances = statuses.mapIndexed { index, status -> instance(is4k = index % 2 == 1, status = status) })

        // Processing, PartiallyAvailable and Available are the only three with files.
        composeTestRule.onAllNodesWithText(deleteFilesLabel()).assertCountEquals(3)
    }

    /** The exact bug reported: a downloaded standard copy and an undownloaded 4K copy of the same title. */
    @Test
    fun `a downloaded standard copy offers the button, an undownloaded 4K copy of the same title does not`() {
        val reported = mutableListOf<Boolean>()
        setContent(
            instances =
                listOf(
                    instance(is4k = false, status = SeerrMediaStatusCode.Available),
                    instance(is4k = true, status = SeerrMediaStatusCode.Pending),
                ),
            onDeleteFiles = { is4k -> reported += is4k },
        )

        composeTestRule.onAllNodesWithText(deleteFilesLabel()).assertCountEquals(1)
        composeTestRule.onNodeWithText(deleteFilesLabel()).performClick()
        assertEquals(listOf(false), reported)
    }

    /** Without permission, state never matters — the record-level flag still wins. */
    @Test
    fun `the record-level flag still gates delete files regardless of state`() {
        setContent(
            instances = listOf(instance(is4k = false, status = SeerrMediaStatusCode.Available)),
            canDeleteFiles = false,
        )

        composeTestRule.onAllNodesWithText(deleteFilesLabel()).assertCountEquals(0)
    }

    private fun setContent(
        instances: List<MediaInstance>,
        canDeleteFiles: Boolean = true,
        onDeleteFiles: (is4k: Boolean) -> Unit = {},
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                RequestActionsContent(
                    model =
                        RequestSheetModel(
                            item = item(),
                            actions = RequestActions(),
                            media =
                                MediaRecord(
                                    mediaId = MEDIA_ID,
                                    isTv = true,
                                    instances = instances,
                                    canSetStatus = false,
                                    canClearData = false,
                                    canDeleteFiles = canDeleteFiles,
                                ),
                        ),
                    callbacks =
                        RequestSheetCallbacks(
                            onApprove = {},
                            onRetry = {},
                            onDecline = {},
                            onRemove = {},
                            onDeleteFiles = onDeleteFiles,
                        ),
                    blockTitle = false,
                    onBlockTitleChange = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun deleteFilesLabel() =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.media_delete_files)

    private fun instance(
        is4k: Boolean,
        status: SeerrMediaStatusCode?,
    ) = MediaInstance(is4k = is4k, status = status, serviceUrl = null, mediaServerUrl = null, watch = null)

    private fun item() =
        RequestItem(
            id = REQUEST_ID,
            tmdbId = TMDB_ID,
            mediaType = RequestMediaType.Tv,
            title = "Breaking Bad",
            posterUrl = null,
            year = "2008",
            requestedBy = null,
            requestedById = null,
            requestedAtMillis = null,
            status = null,
            mediaStatus = null,
            download = null,
            seasonNumbers = emptyList(),
            is4k = false,
        )

    private companion object {
        const val MEDIA_ID = 900
        const val REQUEST_ID = 11
        const val TMDB_ID = 1396
    }
}
