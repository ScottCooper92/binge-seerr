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
import com.binge.integration.sdk.toAdvancedRequest
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestScreen
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestViewModel

/**
 * The Activity behind `CAPABILITY_ADVANCED_OPTIONS`: Binge starts it for a result with a title,
 * this app shows its own picker and submits, and answers `RESULT_OK` once it has. It is a screen
 * the user sees and confirms, so the caller check the exported Service needs has no counterpart
 * here: nothing is served to the caller but a result code. A hand-off that names no title finishes.
 */
class AdvancedRequestActivity : ComponentActivity() {
    private val viewModel: AdvancedRequestViewModel by viewModels {
        AdvancedRequestViewModel.Factory((application as SeerrApp).connection, checkNotNull(intent.toAdvancedRequest()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.toAdvancedRequest() == null) {
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
}
