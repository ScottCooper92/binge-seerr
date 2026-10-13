package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.seerr.SeerrMetadataTestResultDto
import com.cooper.binge.companion.seerr.seerr.peekedBody
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject

/** The metadata page: which provider serves series and which anime, and a test that reaches the ones the draft would use. */
@HiltViewModel
class MetadataViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @ApplicationScope appScope: CoroutineScope,
    ) : ExtrasEditorViewModel<MetadataForm, MetadataExtras>(MetadataExtras(), dispatcher) {
        /** Metadata saves as it changes (#930): it is two provider choices, and Test is an action beside them. */
        override val saveAsMadeScope: CoroutineScope = appScope

        init {
            reload()
        }

        override suspend fun load(): MetadataForm = connection.api().metadataSettings().toForm()

        override suspend fun write(draft: MetadataForm): MetadataForm = connection.api().updateMetadataSettings(draft.toDto()).toForm()

        /** The results a failed test still carries in its body, or null where it carries none. */
        private fun HttpException.testResult(): SeerrMetadataTestResultDto? =
            attempt { RESULT_JSON.decodeFromString<SeerrMetadataTestResultDto>(peekedBody()) }.getOrNull()

        fun test() {
            val draft = ready()?.draft ?: return
            if (currentExtras().testing) return
            editExtras { it.copy(testing = true) }
            viewModelScope.launch(dispatcher) {
                val outcome = attempt { connection.api().testMetadataProviders(draft.testBody()) }
                val result = outcome.getOrNull() ?: (outcome.exceptionOrNull() as? HttpException)?.testResult()
                editExtras {
                    it.copy(
                        testing = false,
                        tmdb = result?.tests?.tmdb.toProviderCheck(),
                        tvdb = result?.tests?.tvdb.toProviderCheck(),
                    )
                }
                outcome
                    .onSuccess { notify(EditorEvent.Notice(R.string.server_settings_metadata_tested)) }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }
    }

private val RESULT_JSON = Json { ignoreUnknownKeys = true }
