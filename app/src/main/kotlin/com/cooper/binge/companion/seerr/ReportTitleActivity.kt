package com.cooper.binge.companion.seerr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.handoff.TitleReportLink
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleActions
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleSheet
import com.cooper.binge.companion.seerr.ui.handoff.ReportTitleViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

/**
 * The phone's end of "Report an issue" from a TV (#1511): a code this app scanned names a title and a server, and this shows
 * the report form for it when that server is the one this phone is signed in to.
 *
 * Not exported, so only this app's own scan can start it. The extras are what the scan parsed, never the raw text, and the
 * server in them is only compared with the connected one, never contacted.
 */
@AndroidEntryPoint
class ReportTitleActivity : ComponentActivity() {
    private val viewModel: ReportTitleViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<ReportTitleViewModel.Factory> { factory ->
                factory.create(checkNotNull(linkOrNull()))
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (linkOrNull() == null) {
            finish()
            return
        }
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SeerrTheme {
                ReportTitleSheet(
                    state = state,
                    actions =
                        ReportTitleActions(
                            onSend = viewModel::send,
                            onOpenApp = {
                                packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
                                finish()
                            },
                            onClose = ::finish,
                        ),
                )
            }
        }
    }

    private fun linkOrNull(): TitleReportLink? {
        val server = intent?.getStringExtra(EXTRA_SERVER) ?: return null
        val mediaType = intent?.getStringExtra(EXTRA_MEDIA_TYPE) ?: return null
        val tmdbId = intent?.getIntExtra(EXTRA_TMDB_ID, 0)?.takeIf { it > 0 } ?: return null
        return TitleReportLink(server, mediaType, tmdbId, intent?.getBooleanExtra(EXTRA_IS_4K, false) == true)
    }

    companion object {
        private const val EXTRA_SERVER = "server"
        private const val EXTRA_MEDIA_TYPE = "media_type"
        private const val EXTRA_TMDB_ID = "tmdb_id"
        private const val EXTRA_IS_4K = "is_4k"

        fun intent(
            context: Context,
            link: TitleReportLink,
        ): Intent =
            Intent(context, ReportTitleActivity::class.java)
                .putExtra(EXTRA_SERVER, link.server)
                .putExtra(EXTRA_MEDIA_TYPE, link.mediaType)
                .putExtra(EXTRA_TMDB_ID, link.tmdbId)
                .putExtra(EXTRA_IS_4K, link.is4k)
    }
}
