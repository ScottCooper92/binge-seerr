package com.cooper.binge.companion.seerr.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The question asked once, before setup and before the hub: may this app share usage data and crash reports. [consent] is null while
 * the stored answer is read, and [AnalyticsConsent.UNDECIDED] asks. An install that already answered is not asked again, so one that
 * updates into the combined question keeps what it has (see `CrashReportingMigration`). One answer records both, in one write.
 */
@HiltViewModel
class ConsentViewModel
    @Inject
    constructor(
        private val prefs: TelemetryPrefs,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val consent: StateFlow<AnalyticsConsent?> =
            prefs.analyticsConsent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        fun answer(granted: Boolean) {
            viewModelScope.launch(dispatcher) { prefs.answerFirstRun(granted) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
