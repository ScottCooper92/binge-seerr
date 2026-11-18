package com.cooper.binge.companion.seerr.ui.consent

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.core.telemetry.TelemetryPrefs
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
        prefs =
            TelemetryPrefs(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    folder.newFile("t.preferences_pb")
                },
                crashReportingDefault = false,
            )
        val vm = ConsentViewModel(prefs, mainDispatcherRule.dispatcher)
        viewModels.put("consent", vm)
        backgroundScope.launch { vm.consent.collect {} }
        return vm
    }

    @Test
    fun `an unanswered install is asked`() =
        runTest {
            val vm = viewModel()
            vm.consent.first { it == AnalyticsConsent.UNDECIDED }
        }

    @Test
    fun `agreeing turns usage data and crash reports on in one answer`() =
        runTest {
            val vm = viewModel()
            vm.consent.first { it == AnalyticsConsent.UNDECIDED }

            vm.answer(true)

            vm.consent.first { it == AnalyticsConsent.GRANTED }
            assertEquals(true, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `not now turns both off, and crash reports stay off`() =
        runTest {
            val vm = viewModel()
            vm.consent.first { it == AnalyticsConsent.UNDECIDED }

            vm.answer(false)

            vm.consent.first { it == AnalyticsConsent.DENIED }
            assertEquals(false, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `an install that already answered is not asked again`() =
        runTest {
            val vm = viewModel()
            prefs.setAnalyticsGranted(true)
            vm.consent.first { it == AnalyticsConsent.GRANTED }
        }
}
