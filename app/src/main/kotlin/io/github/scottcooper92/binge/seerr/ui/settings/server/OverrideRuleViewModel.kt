package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrServiceSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Matches `UserAdmission`'s `ALL_USERS_TAKE`: the "requested by" picker needs every user, not one page of them. */
private const val USERS_PAGE = 1000

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
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val id: Int?,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ExtrasEditorViewModel<OverrideRuleForm, OverrideRuleExtras>(OverrideRuleExtras(), dispatcher) {
        /** Filled by [load] so [loadChoices] can reuse the same fetch instead of re-fetching per instance pick. */
        private var radarrRecords: List<SeerrServiceSettingsDto> = emptyList()
        private var sonarrRecords: List<SeerrServiceSettingsDto> = emptyList()

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
                radarrRecords = radarr.await()
                sonarrRecords = sonarr.await()
                val instances =
                    radarrRecords.mapNotNull { it.toSummary(ServiceType.Radarr) } +
                        sonarrRecords.mapNotNull { it.toSummary(ServiceType.Sonarr) }
                val userChoices = users.await().map { user -> Choice(user.id, user.displayName ?: user.username ?: user.id.toString()) }
                editExtras { it.copy(instances = instances, users = userChoices) }
                form.serviceId?.let { serviceId -> form.serviceType?.let { type -> loadChoices(type, serviceId) } }
                form
            }

        override suspend fun write(draft: OverrideRuleForm): OverrideRuleForm {
            val api = connection.api()
            val body = draft.toDto()
            val creating = draft.id == null
            crashBreadcrumbs.log("${if (creating) "creating" else "updating"} override rule")
            val answered = if (creating) api.createOverrideRule(body) else api.updateOverrideRule(draft.id, body)
            analytics.event(
                AnalyticsEvents.OVERRIDE_RULE_CHANGED,
                mapOf(AnalyticsEvents.PARAM_ACTION to if (creating) "created" else "updated"),
            )
            return answered.toForm()
        }

        override fun canSave(draft: OverrideRuleForm): Boolean = draft.valid

        fun selectInstance(instance: DvrSummary) {
            edit { it.copy(serviceType = instance.type, serviceId = instance.id, profileId = null, rootFolder = null, tagIds = emptySet()) }
            viewModelScope.launch(dispatcher) { loadChoices(instance.type, instance.id) }
        }

        fun toggleUser(userId: Int) = edit { it.copy(userIds = it.userIds.toggled(userId)) }

        fun toggleTag(tagId: Int) = edit { it.copy(tagIds = it.tagIds.toggled(tagId)) }

        fun delete() {
            val existing = ready()?.draft?.id ?: return
            crashBreadcrumbs.key("override_rule_id", existing.toString())
            crashBreadcrumbs.log("deleting override rule")
            viewModelScope.launch(dispatcher) {
                runCatching { connection.api().deleteOverrideRule(existing) }
                    .onSuccess {
                        analytics.event(AnalyticsEvents.OVERRIDE_RULE_CHANGED, mapOf(AnalyticsEvents.PARAM_ACTION to "deleted"))
                        notify(EditorEvent.Deleted)
                    }.onFailure { failure -> notify(EditorEvent.Failed(failure.toSeerrError())) }
            }
        }

        /** The instance's stored connection is what the admin's read of it carries, so no key is typed here. */
        private suspend fun loadChoices(
            type: ServiceType,
            serviceId: Int,
        ) {
            editExtras { it.copy(choices = null, loadingChoices = true) }
            val records = if (type == ServiceType.Radarr) radarrRecords else sonarrRecords
            val choices =
                runCatching {
                    val record = records.firstOrNull { it.id == serviceId } ?: throw NoSuchElementException("instance $serviceId")
                    connection.api().testDvr(type.apiSegment, record.toForm(type).toTestBody()).toChoices()
                }.getOrNull()
            editExtras { it.copy(choices = choices, loadingChoices = false) }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: Int?): OverrideRuleViewModel
        }
    }
