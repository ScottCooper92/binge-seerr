package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrCreateUserBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrImportJellyfinBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrImportPlexBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrJellyfinUserDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrPlexUserDto
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The web client reads the whole user list to know who is imported; this asks for it in one page. */
private const val ALL_USERS_TAKE = 1000

/**
 * Adding users: a local account, or an import of the media server's accounts. The plex.tv list
 * arrives already filtered to the unknown; the Jellyfin one does not, so it is filtered here
 * against the server's users. [onAdmitted] fires after a success so the owner can refresh the list.
 */
class UserAdmission(
    private val scope: CoroutineScope,
    private val connection: SeerrConnection,
    private val onAdmitted: () -> Unit,
) {
    private val stateFlow = MutableStateFlow<UserAdmissionState?>(null)
    val state: StateFlow<UserAdmissionState?> = stateFlow.asStateFlow()

    private val eventFlow = MutableSharedFlow<UsersEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UsersEvent> = eventFlow.asSharedFlow()

    fun start() {
        if (stateFlow.value == null) stateFlow.value = UserAdmissionState.Choosing()
    }

    fun cancel() {
        if (stateFlow.value?.saving != true) stateFlow.value = null
    }

    fun startCreate(canGeneratePassword: Boolean) {
        stateFlow.value = UserAdmissionState.Creating(CreateUserDraft(canGeneratePassword = canGeneratePassword))
    }

    fun editDraft(transform: (CreateUserDraft) -> CreateUserDraft) =
        stateFlow.update { current ->
            val creating = current as? UserAdmissionState.Creating ?: return@update current
            if (creating.saving) creating else creating.copy(draft = transform(creating.draft))
        }

    fun create() {
        val creating = stateFlow.value as? UserAdmissionState.Creating ?: return
        val draft = creating.draft
        if (creating.saving || !draft.valid) return
        stateFlow.value = creating.copy(saving = true)
        scope.launch {
            runCatching {
                val body =
                    SeerrCreateUserBody(
                        email = draft.email.trim(),
                        username = draft.username.trim(),
                        password = draft.password.takeUnless { draft.generatePassword },
                    )
                connection.api().createUser(body)
            }.onSuccess { created ->
                stateFlow.value = null
                onAdmitted()
                eventFlow.emit(UsersEvent.UserCreated(created.toUserItem()?.name ?: draft.username.trim()))
            }.onFailure { failure ->
                stateFlow.update { current -> (current as? UserAdmissionState.Creating)?.copy(saving = false) ?: current }
                eventFlow.emit(UsersEvent.Failed(failure.toSeerrError()))
            }
        }
    }

    fun startImport(source: UserOrigin) {
        stateFlow.value = UserAdmissionState.Importing(ImportPicker(source))
        scope.launch {
            val candidates =
                runCatching {
                    val api = connection.api()
                    when (source) {
                        UserOrigin.Plex -> api.plexUsers().map { it.toCandidate() }
                        else -> {
                            val known = api.users(take = ALL_USERS_TAKE).results.mapNotNullTo(mutableSetOf()) { it.jellyfinUserId }
                            api.jellyfinUsers().filterNot { it.id in known }.map { it.toCandidate() }
                        }
                    }
                }
            stateFlow.update { current ->
                val importing = current as? UserAdmissionState.Importing ?: return@update current
                if (importing.picker.source != source) return@update current
                importing.copy(
                    picker =
                        candidates.fold(
                            onSuccess = { importing.picker.copy(candidates = it, failed = false) },
                            onFailure = { importing.picker.copy(candidates = null, failed = true) },
                        ),
                )
            }
        }
    }

    fun toggleCandidate(id: String) =
        stateFlow.update { current ->
            val importing = current as? UserAdmissionState.Importing ?: return@update current
            if (importing.saving) return@update current
            val selected = importing.picker.selected
            importing.copy(picker = importing.picker.copy(selected = if (id in selected) selected - id else selected + id))
        }

    fun selectAllCandidates(select: Boolean) =
        stateFlow.update { current ->
            val importing = current as? UserAdmissionState.Importing ?: return@update current
            if (importing.saving) return@update current
            val all =
                importing.picker.candidates
                    .orEmpty()
                    .mapTo(mutableSetOf()) { it.id }
            importing.copy(picker = importing.picker.copy(selected = if (select) all else emptySet()))
        }

    fun import() {
        val importing = stateFlow.value as? UserAdmissionState.Importing ?: return
        val ids = importing.picker.selected.toList()
        if (importing.saving || ids.isEmpty()) return
        stateFlow.value = importing.copy(saving = true)
        scope.launch {
            runCatching {
                val api = connection.api()
                when (importing.picker.source) {
                    UserOrigin.Plex -> api.importFromPlex(SeerrImportPlexBody(ids)).createdCount()
                    else -> api.importFromJellyfin(SeerrImportJellyfinBody(ids)).size
                }
            }.onSuccess { count ->
                stateFlow.value = null
                onAdmitted()
                eventFlow.emit(UsersEvent.UsersImported(count))
            }.onFailure { failure ->
                stateFlow.update { current -> (current as? UserAdmissionState.Importing)?.copy(saving = false) ?: current }
                eventFlow.emit(UsersEvent.Failed(failure.toSeerrError()))
            }
        }
    }
}

/** Overseerr answers the created list; the Jellyseerr lineage wraps it with the refreshed ones. */
private fun JsonElement.createdCount(): Int =
    when (this) {
        is JsonArray -> size
        is JsonObject -> (this["createdUsers"] as? JsonArray)?.size ?: 0
        else -> 0
    }

private fun SeerrPlexUserDto.toCandidate(): ImportCandidate =
    ImportCandidate(
        id = id,
        name = listOfNotNull(title, username, email?.substringBefore('@')).firstOrNull { it.isNotBlank() } ?: id,
        email = email?.takeIf { it.isNotBlank() },
        avatarUrl = thumb?.takeIf { it.startsWith("http") },
    )

private fun SeerrJellyfinUserDto.toCandidate(): ImportCandidate =
    ImportCandidate(
        id = id,
        name = username?.takeIf { it.isNotBlank() } ?: id,
        email = email?.takeIf { it.isNotBlank() },
        avatarUrl = thumb?.takeIf { it.startsWith("http") },
    )
