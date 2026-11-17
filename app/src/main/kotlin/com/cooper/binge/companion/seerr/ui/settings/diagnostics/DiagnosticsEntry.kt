package com.cooper.binge.companion.seerr.ui.settings.diagnostics

import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.presentation.diagnostics.DiagnosticsViewModel
import com.cooper.homelab.core.uiandroid.diagnostics.DiagnosticsEffects
import com.cooper.homelab.core.uiandroid.diagnostics.DiagnosticsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Holds the shared Diagnostics view model on [viewModelScope], so it is made once and survives a rotation. [preamble] is what the
 * page and the shared text carry above the log: this app's version and the device. No address, credential or user is in it.
 */
@HiltViewModel
internal class DiagnosticsHost
    @Inject
    constructor(
        log: RingLog,
    ) : ViewModel() {
        val model = DiagnosticsViewModel(viewModelScope, log, preamble = ::preamble)

        private fun preamble(): List<String> =
            listOf(
                "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
            )
    }

/** The Diagnostics page: the app's recent log, with Copy and Share, over the shared screen. Nothing is uploaded. */
@Composable
internal fun DiagnosticsEntry(
    onBack: () -> Unit,
    host: DiagnosticsHost = hiltViewModel(),
) {
    val state by host.model.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    DiagnosticsEffects(host.model.events, snackbarHostState)
    DiagnosticsScreen(
        state = state,
        onSetLevel = host.model::setMinimumLevel,
        onCopy = host.model::copy,
        onShare = host.model::share,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}
