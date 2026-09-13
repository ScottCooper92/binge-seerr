package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrServiceSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One Radarr or Sonarr instance, new ([id] null) or existing: the connection is typed, then a
 * test reaches the instance and answers with its profiles, folders and tags, and only then can
 * the destination be picked and the record saved — the web client's own order. An existing
 * instance is tested on load, so its pickers are filled without a tap.
 */
@HiltViewModel(assistedFactory = DvrInstanceViewModel.Factory::class)
class DvrInstanceViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @Assisted private val type: ServiceType,
        @Assisted private val id: Int?,
    ) : EditorViewModel<DvrForm>() {
        private val extrasState = MutableStateFlow(DvrExtras())
        val extras: StateFlow<DvrExtras> = extrasState.asStateFlow()

        private val deletedState = MutableStateFlow(false)

        /** True once the instance is gone from the server; the page leaves on it. */
        val deleted: StateFlow<Boolean> = deletedState.asStateFlow()

        init {
            reload()
        }

        override suspend fun load(): DvrForm {
            val form =
                if (id == null) {
                    DvrForm.blank(type)
                } else {
                    connection
                        .api()
                        .instances(type)
                        .firstOrNull { it.id == id }
                        ?.toForm(type)
                        ?: throw NoSuchElementException("instance $id")
                }
            if (form.connectionValid) {
                runCatching { connection.api().testDvr(type.apiSegment, form.toTestBody()).toChoices() }
                    .onSuccess { choices -> extrasState.update { it.copy(choices = choices) } }
            }
            return form
        }

        override suspend fun write(draft: DvrForm): DvrForm {
            val api = connection.api()
            val body = draft.toDto(extrasState.value.choices)
            val answered = if (draft.id == null) api.createDvr(type.apiSegment, body) else api.updateDvr(type.apiSegment, draft.id, body)
            return answered.toForm(type)
        }

        override fun canSave(draft: DvrForm): Boolean = draft.valid

        /** Reaches the instance with what is typed; a pick made against an earlier test is kept where it still exists. */
        fun test() {
            val draft = ready()?.draft ?: return
            if (!draft.connectionValid || extrasState.value.testing) return
            extrasState.update { it.copy(testing = true) }
            viewModelScope.launch {
                runCatching { connection.api().testDvr(type.apiSegment, draft.toTestBody()).toChoices() }
                    .onSuccess { choices ->
                        extrasState.update { it.copy(choices = choices, testing = false) }
                        edit { form -> form.reconciledWith(choices) }
                        notify(EditorEvent.Notice(R.string.server_settings_dvr_tested))
                    }.onFailure { failure ->
                        extrasState.update { it.copy(testing = false) }
                        notify(EditorEvent.Failed(failure.toSeerrError()))
                    }
            }
        }

        fun delete() {
            val existing = id ?: return
            viewModelScope.launch {
                runCatching { connection.api().deleteDvr(type.apiSegment, existing) }
                    .onSuccess { deletedState.value = true }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(
                type: ServiceType,
                id: Int?,
            ): DvrInstanceViewModel
        }
    }

/** A pick the instance no longer offers is dropped; a missing one takes the first on offer, as the web client does. */
internal fun DvrForm.reconciledWith(choices: DvrChoices): DvrForm =
    copy(
        profileId = choices.profiles.firstOrNull { it.id == profileId }?.id ?: choices.profiles.firstOrNull()?.id,
        rootFolder = choices.rootFolders.firstOrNull { it == rootFolder } ?: choices.rootFolders.firstOrNull(),
        tagIds = tagIds.filterTo(mutableSetOf()) { id -> choices.tags.any { it.id == id } },
        animeProfileId = animeProfileId?.takeIf { id -> choices.profiles.any { it.id == id } },
        animeRootFolder = animeRootFolder?.takeIf { it in choices.rootFolders },
        animeTagIds = animeTagIds?.filterTo(mutableSetOf()) { id -> choices.tags.any { it.id == id } },
        languageProfileId = languageProfileId?.takeIf { id -> choices.languageProfiles?.any { it.id == id } == true },
    )

internal suspend fun io.github.scottcooper92.binge.seerr.seerr.SeerrApi.instances(type: ServiceType): List<SeerrServiceSettingsDto> =
    if (type == ServiceType.Radarr) radarrSettings() else sonarrSettings()
