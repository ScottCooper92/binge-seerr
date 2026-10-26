package com.cooper.binge.companion.seerr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.companion.sdk.toAdvancedRequest
import com.cooper.binge.companion.seerr.flags.FeatureFlags
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.AdvancedRequestScreen
import com.cooper.binge.companion.seerr.ui.AdvancedRequestUiState
import com.cooper.binge.companion.seerr.ui.AdvancedRequestViewModel
import com.cooper.binge.companion.seerr.ui.tv.TvAdvancedRequestActions
import com.cooper.binge.companion.seerr.ui.tv.TvAdvancedRequestShell
import com.cooper.binge.companion.seerr.ui.tv.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import javax.inject.Inject

/**
 * The Activity behind `CAPABILITY_ADVANCED_OPTIONS`: Binge starts it for a result with a title,
 * this app shows its own picker and submits, and answers `RESULT_OK` once it has. Exported and
 * resolvable by action, so it checks its caller the way the Service does before it reads the
 * extras — debug-permissive, pinned in release — and a hand-off from anyone else, or one that
 * names no title, finishes cancelled. A television gets the D-pad picker under the TV theme, bound
 * to the same ViewModel.
 */
@AndroidEntryPoint
class AdvancedRequestActivity : ComponentActivity() {
    @Inject
    lateinit var featureFlags: FeatureFlags

    private val viewModel: AdvancedRequestViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AdvancedRequestViewModel.Factory> {
                it.create(checkNotNull(intent.toAdvancedRequest()))
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!bingeHandOffPolicy(featureFlags).permits(callingPackage) || intent.toAdvancedRequest() == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        drawEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state) {
                if (state is AdvancedRequestUiState.Submitted) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
            val onOpenSetup = {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            if (LocalConfiguration.current.isTelevision()) {
                TvAdvancedRequestShell(
                    state = state,
                    actions =
                        TvAdvancedRequestActions(
                            onSelectServer = viewModel::selectServer,
                            onSelectProfile = viewModel::selectProfile,
                            onSelectRootFolder = viewModel::selectRootFolder,
                            onSubmit = viewModel::submit,
                            onOpenSetup = onOpenSetup,
                            onClose = ::finish,
                        ),
                )
                return@setContent
            }
            SeerrTheme {
                AdvancedRequestScreen(
                    state = state,
                    onSelectServer = viewModel::selectServer,
                    onSelectProfile = viewModel::selectProfile,
                    onSelectRootFolder = viewModel::selectRootFolder,
                    onSubmit = viewModel::submit,
                    onOpenSetup = onOpenSetup,
                    onClose = ::finish,
                )
            }
        }
    }
}
