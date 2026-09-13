package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val USERS_PAGE = 100

/**
 * One override rule, new ([id] null) or existing: an instance, the conditions a request must
 * meet, and what to override on it. The overrides are picked from what the chosen instance offers,
 * reached through the same test call the instance page uses, with the credentials the server holds.
 */
@HiltViewModel(assistedFactory = OverrideRuleViewModel.Factory::class)
class OverrideRuleViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @Assisted private val id: Int?,
    ) : EditorViewModel<OverrideRuleForm>() {
        private val extrasState = MutableStateFlow(OverrideRuleExtras())
        val extras: StateFlow<OverrideRuleExtras> = extrasState.asStateFlow()

        private val deletedState = MutableStateFlow(false)
        val deleted: StateFlow<Boolean> = deletedState.asStateFlow()

        init {
            reload()
        }

        override suspend fun load(): OverrideRuleForm =
            coroutineScope {
                val api = connection.api()
                val radarr = async { api.radarrSettings() }
                val sonarr = async { api.sonarrSettings() }
                val users = async { runCatching { api.users(take = USERS_PAGE).results }.getOrDefault(emptyList()) }
                val form =
                    if (id ==
                        null
                    ) {
                        OverrideRuleForm()
                    } else {
                        api.overrideRules().firstOrNull { it.id == id }?.toForm()
                            ?: throw NoSuchElementException("rule $id")
                    }
                val instances =
                    radarr.await().mapNotNull { it.toSummary(ServiceType.Radarr) } +
                        sonarr.await().mapNotNull { it.toSummary(ServiceType.Sonarr) }
                extrasState.update {
                    it.copy(
                        instances = instances,
                        users = users.await().map { user -> Choice(user.id, user.displayName ?: user.username ?: user.id.toString()) },
                    )
                }
                form.serviceId?.let { serviceId -> form.serviceType?.let { type -> loadChoices(type, serviceId) } }
                form
            }

        override suspend fun write(draft: OverrideRuleForm): OverrideRuleForm {
            val api = connection.api()
            val body = draft.toDto()
            val answered = if (draft.id == null) api.createOverrideRule(body) else api.updateOverrideRule(draft.id, body)
            return answered.toForm()
        }

        override fun canSave(draft: OverrideRuleForm): Boolean = draft.valid

        fun selectInstance(instance: DvrSummary) {
            edit { it.copy(serviceType = instance.type, serviceId = instance.id, profileId = null, rootFolder = null, tagIds = emptySet()) }
            viewModelScope.launch { loadChoices(instance.type, instance.id) }
        }

        fun toggleUser(userId: Int) = edit { it.copy(userIds = if (userId in it.userIds) it.userIds - userId else it.userIds + userId) }

        fun toggleTag(tagId: Int) = edit { it.copy(tagIds = if (tagId in it.tagIds) it.tagIds - tagId else it.tagIds + tagId) }

        fun delete() {
            val existing = id ?: return
            viewModelScope.launch {
                runCatching { connection.api().deleteOverrideRule(existing) }
                    .onSuccess { deletedState.value = true }
                    .onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        /** The instance's stored connection is what the admin's read of it carries, so no key is typed here. */
        private suspend fun loadChoices(
            type: ServiceType,
            serviceId: Int,
        ) {
            extrasState.update { it.copy(choices = null, loadingChoices = true) }
            val api = connection.api()
            val choices =
                runCatching {
                    val record =
                        api.instances(type).firstOrNull { it.id == serviceId } ?: throw NoSuchElementException("instance $serviceId")
                    api.testDvr(type.apiSegment, record.toForm(type).toTestBody()).toChoices()
                }.getOrNull()
            extrasState.update { it.copy(choices = choices, loadingChoices = false) }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: Int?): OverrideRuleViewModel
        }
    }
