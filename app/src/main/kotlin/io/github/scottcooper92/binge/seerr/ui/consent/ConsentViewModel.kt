package io.github.scottcooper92.binge.seerr.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsConsent
import io.github.scottcooper92.binge.seerr.telemetry.TelemetryPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the app still has to ask about usage data before anything else is shown. */
sealed interface ConsentUiState {
    data object Loading : ConsentUiState

    data object Asking : ConsentUiState

    data object Decided : ConsentUiState
}

/**
 * The question asked once, before setup and before the hub: may this app share usage data. It
 * asks while the answer is undecided, so an install that updates into it is asked too.
 */
@HiltViewModel
class ConsentViewModel
    @Inject
    constructor(
        private val prefs: TelemetryPrefs,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val uiState: StateFlow<ConsentUiState> =
            prefs.analyticsConsent
                .map { if (it == AnalyticsConsent.UNDECIDED) ConsentUiState.Asking else ConsentUiState.Decided }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConsentUiState.Loading)

        fun answer(granted: Boolean) {
            viewModelScope.launch(dispatcher) { prefs.setAnalyticsGranted(granted) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
