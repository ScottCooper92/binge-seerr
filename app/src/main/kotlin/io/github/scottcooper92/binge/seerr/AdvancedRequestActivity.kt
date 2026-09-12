package io.github.scottcooper92.binge.seerr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.integration.sdk.BingeHosts
import com.binge.integration.sdk.HandOffPolicy
import com.binge.integration.sdk.toAdvancedRequest
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestScreen
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestViewModel

/**
 * The Activity behind `CAPABILITY_ADVANCED_OPTIONS`: Binge starts it for a result with a title,
 * this app shows its own picker and submits, and answers `RESULT_OK` once it has. Exported and
 * resolvable by action, so it checks its caller the way the Service does before it reads the
 * extras — debug-permissive, pinned in release — and a hand-off from anyone else, or one that
 * names no title, finishes cancelled.
 */
@AndroidEntryPoint
class AdvancedRequestActivity : ComponentActivity() {
    private val viewModel: AdvancedRequestViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AdvancedRequestViewModel.Factory> {
                it.create(checkNotNull(intent.toAdvancedRequest()))
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!callerPolicy().permits(callingPackage) || intent.toAdvancedRequest() == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        setContent {
            BingeExpressiveTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state) {
                    if (state is AdvancedRequestUiState.Submitted) {
                        setResult(RESULT_OK)
                        finish()
                    }
                }
                AdvancedRequestScreen(
                    state = state,
                    onSelectServer = viewModel::selectServer,
                    onSelectProfile = viewModel::selectProfile,
                    onSelectRootFolder = viewModel::selectRootFolder,
                    onSubmit = viewModel::submit,
                    onOpenSetup = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun callerPolicy() =
        if (BuildConfig.DEBUG) HandOffPolicy.anyCaller(TAG) else HandOffPolicy.pinned(this, listOf(BingeHosts.release))

    private companion object {
        const val TAG = "SeerrCompanion"
    }
}
