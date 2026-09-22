package io.github.scottcooper92.binge.seerr.ui.consent

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsConsent
import io.github.scottcooper92.binge.seerr.telemetry.TelemetryPrefs
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConsentViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()
    private lateinit var prefs: TelemetryPrefs

    @After
    fun tearDown() = viewModels.clear()

    private fun TestScope.viewModel(): ConsentViewModel {
        prefs = TelemetryPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("t.preferences_pb") })
        val vm = ConsentViewModel(prefs, mainDispatcherRule.dispatcher)
        viewModels.put("consent", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `an unanswered install is asked, and either answer is kept and ends the question`() =
        runTest {
            val vm = viewModel()
            vm.uiState.first { it == ConsentUiState.Asking }

            vm.answer(false)
            vm.uiState.first { it == ConsentUiState.Decided }
            assertEquals(AnalyticsConsent.DENIED, prefs.analyticsConsent.first())

            vm.answer(true)
            prefs.analyticsConsent.first { it == AnalyticsConsent.GRANTED }
            assertEquals(ConsentUiState.Decided, vm.uiState.value)
        }

    @Test
    fun `an install that already answered is not asked again`() =
        runTest {
            val vm = viewModel()
            prefs.setAnalyticsGranted(true)
            vm.uiState.first { it == ConsentUiState.Decided }
        }
}
