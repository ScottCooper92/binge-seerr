package io.github.scottcooper92.binge.seerr.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.ListRefreshes
import io.github.scottcooper92.binge.seerr.data.USERS_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.data.UsersRemoteMediator
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.PermissionScope
import io.github.scottcooper92.binge.seerr.seerr.SeerrBulkUsersBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.permissionScope
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.users.settings.lockedFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the browser needs once per connection: which toggles the server offers, and what the viewer may add. */
private data class UsersScope(
    /** Until the profile is read, the blocklist toggles stay hidden: Overseerr has none. */
    val permissions: PermissionScope = PermissionScope(blocklist = false),
    val canAdmit: Boolean = false,
    val importSource: UserOrigin? = null,
    val canGeneratePassword: Boolean = false,
    /** The toggles this viewer may not flip. Until the viewer is read, Admin, which only the owner may grant or revoke. */
    val locked: Set<ManageablePermission> = setOf(ManageablePermission.Admin),
    /** Whether the viewer is the server's owner, user 1: the only one whose bulk save may carry Admin or reach user 1 (#1008). */
    val isOwner: Boolean = false,
)

/** Where resolving the [UsersScope] stands: in flight, done, or failed with why, so a failure is a state rather than a guessed scope. */
private sealed interface ScopeState {
    data object Resolving : ScopeState

    data class Resolved(
        val scope: UsersScope,
    ) : ScopeState

    data class Failed(
        val error: SeerrError,
    ) : ScopeState
}

/** The scope for [viewer], with what the server offers read from [profile] where it could be read. */
private fun usersScope(
    viewer: SeerrUserDto,
    profile: SeerrServerProfile?,
): UsersScope {
    val permissions = viewer.toPermissions()
    val settings = profile?.settings
    return UsersScope(
        permissions = profile?.permissionScope() ?: PermissionScope(blocklist = false),
        canAdmit = permissions.canManageUsers,
        // The import lists live under /settings, which needs ADMIN; adding one account needs only MANAGE_USERS (#1009).
        importSource =
            when (profile?.mediaServer.takeIf { permissions.isAdmin }) {
                SeerrMediaServer.Plex -> UserOrigin.Plex
                SeerrMediaServer.Jellyfin -> UserOrigin.Jellyfin
                SeerrMediaServer.Emby -> UserOrigin.Emby
                SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown, null -> null
            },
        canGeneratePassword = settings?.emailEnabled == true && !settings.applicationUrl.isNullOrBlank(),
        locked = lockedFor(isOwner = viewer.id == OWNER_USER_ID),
        isOwner = viewer.id == OWNER_USER_ID,
    )
}

/**
 * The users browser: one cached, sorted list read from the cache and refreshed through the
 * mediator, and the bulk edit, which re-applies the chosen toggles onto each selected user's own
 * cached bitmask — never onto a value shared across the selection — and moves their cached rows
 * with it. A failed `auth/me` read is a [UsersUiState.Error] the user can retry, and it also self-corrects the next
 * time the screen becomes visible.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
@HiltViewModel
class UsersViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val store: UserStore,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val selectedSort = MutableStateFlow(UserSort.Created)
        private val selection = MutableStateFlow<Set<Int>>(emptySet())
        private val edit = MutableStateFlow<BulkEdit?>(null)

        private val eventFlow = MutableSharedFlow<UsersEvent>(extraBufferCapacity = 1)

        /** Refreshes the list through the mediator: a new user is on the server, not in the cache. */
        private val listVersion = MutableStateFlow(0)

        val admission =
            UserAdmission(scope = viewModelScope, dispatcher = dispatcher, connection = connection, emit = eventFlow::emit) {
                listVersion.update { it + 1 }
            }

        val events: SharedFlow<UsersEvent> = eventFlow.asSharedFlow()

        /** Bumped when the page becomes visible, so the scope is re-read rather than held from the first visit. */
        private val scopeRefresh = MutableStateFlow(0)

        /**
         * Who is looking and what the server offers. A failed `auth/me` is [ScopeState.Failed] rather than a viewer with
         * no permissions, so the screen says so instead of hiding the add action without a word (#1073). A retry from a
         * failure shows [ScopeState.Resolving] again. The profile stays best-effort: a failed read only hides what it
         * would have offered.
         */
        private val scope: StateFlow<ScopeState> =
            scopeRefresh
                .flatMapLatest {
                    flow {
                        if (scope.value is ScopeState.Failed) emit(ScopeState.Resolving)
                        // The refreshing reads, not the cached ones: both caches live as long as the
                        // connection, so re-running this over them would re-read nothing.
                        emit(
                            attempt { connection.refreshAuthenticatedUser() }.fold(
                                onSuccess = { viewer ->
                                    val profile = attempt { connection.refreshProfile() }.getOrNull()
                                    ScopeState.Resolved(usersScope(viewer, profile))
                                },
                                onFailure = { ScopeState.Failed(it.toSeerrError()) },
                            ),
                        )
                    }
                }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.Lazily, ScopeState.Resolving)

        /** The resolved scope, or the defaults while there is none; only a ready screen offers the actions that read it. */
        private val currentScope: UsersScope get() = (scope.value as? ScopeState.Resolved)?.scope ?: UsersScope()

        private val refreshes = ListRefreshes<UserSort>()

        val users: Flow<PagingData<UserItem>> =
            combine(selectedSort, listVersion) { sort, _ -> sort }
                .flatMapLatest { sort ->
                    Pager(
                        config = PagingConfig(pageSize = USERS_PAGE_SIZE),
                        remoteMediator =
                            UsersRemoteMediator(
                                sort = sort.apiValue,
                                api = connection::api,
                                store = store,
                                onRefresh = { rows -> refreshes.record(sort, rows) },
                            ) { dto, key, index ->
                                dto.toUserItem()?.toEntity(key, index)
                            },
                    ) { store.pagingSource(sort.apiValue) }.flow
                }.map { data -> data.map { it.toUserItem() } }
                .cachedIn(viewModelScope)

        val uiState: StateFlow<UsersUiState> =
            combine(
                combine(selectedSort, refreshes.latest) { sort, refreshes -> sort to refreshes[sort] },
                selection,
                edit,
                scope,
                admission.state,
            ) { (sort, refresh), selection, edit, scope, admission ->
                when (scope) {
                    ScopeState.Resolving -> UsersUiState.Loading
                    is ScopeState.Failed -> UsersUiState.Error(scope.error)
                    is ScopeState.Resolved ->
                        UsersUiState.Ready(
                            sort = sort,
                            selection = selection,
                            edit = edit,
                            offered = ManageablePermission.offered(scope.scope.permissions),
                            canAdmit = scope.scope.canAdmit,
                            importSource = scope.scope.importSource,
                            canGeneratePassword = scope.scope.canGeneratePassword,
                            locked = scope.scope.locked,
                            admission = admission,
                            refresh = refresh,
                        )
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, UsersUiState.Loading)

        /**
         * The scope is low-velocity: re-read on entry, never polled. A permission granted or a media
         * server changed in the web client otherwise shows only once the ViewModel is recreated.
         *
         * The list is deliberately left alone. It is Room-backed through [UsersRemoteMediator] and
         * writes made here already land in the cache, so bumping its version would recreate the
         * pager and throw away the scroll for nothing.
         */
        fun setScreenVisible(visible: Boolean) {
            if (visible) scopeRefresh.update { it + 1 }
        }

        /** Re-reads the signed-in user after [UsersUiState.Error]. */
        fun retry() {
            scopeRefresh.update { it + 1 }
        }

        fun setSort(sort: UserSort) {
            selectedSort.value = sort
        }

        fun toggleSelected(userId: Int) = selection.update { if (userId in it) it - userId else it + userId }

        fun clearSelection() {
            selection.value = emptySet()
        }

        /**
         * Opens the editor ticked with what every selected user already has: the intersection, never the union, which would
         * offer each user's permissions to all the others (#1007). A permission only some of them have is [BulkEdit.mixed]: it
         * starts unticked, and the save leaves it as each user has it unless it is toggled. Only the bits the server offers are
         * read: a managed bit the editor hides can't be changed, so it never counts.
         */
        fun startBulkEdit() {
            val ids = selection.value.toList()
            if (ids.isEmpty() || edit.value != null) return
            edit.value = BulkEdit(saving = true)
            val offered = offeredNow().also { offeredAtStart = it }
            viewModelScope.launch(dispatcher) {
                val held =
                    store.permissionsFor(ids).values.map { bitmask ->
                        ManageablePermission.decode(bitmask).filter { it in offered }.toSet()
                    }
                val shared = held.reduceOrNull { acc, permissions -> acc intersect permissions }.orEmpty()
                val any = held.fold(emptySet<ManageablePermission>()) { acc, permissions -> acc + permissions }
                edit.value = BulkEdit(selected = shared, mixed = any - shared)
            }
        }

        fun togglePermission(permission: ManageablePermission) =
            edit.update { current ->
                current?.takeUnless { it.saving || permission in currentScope.locked }?.let {
                    it.toggle(permission)
                }
                    ?: current
            }

        /**
         * What the editor offered when it opened. The seed and the save both filter against this one
         * snapshot, so a scope that changes while the sheet is open can't make the save clear a bit the
         * seed left out.
         */
        private var offeredAtStart: Set<ManageablePermission> = emptySet()

        /** What the editor offers right now; before the profile is read this is the default scope's rows, not none. */
        private fun offeredNow(): Set<ManageablePermission> = (uiState.value as? UsersUiState.Ready)?.offered?.toSet().orEmpty()

        fun cancelBulkEdit() {
            if (edit.value?.saving != true) edit.value = null
        }

        fun applyBulkEdit() {
            val current = edit.value ?: return
            val ids = selection.value.toList()
            if (current.saving || ids.isEmpty()) return
            edit.value = current.copy(saving = true)
            val offered = offeredAtStart
            val isOwner = currentScope.isOwner
            viewModelScope.launch(dispatcher) {
                attempt {
                    // Each id's own cached bitmask is its baseline, and only the permissions the user toggled change it:
                    // every other bit, managed or not, offered or not, keeps that user's own value. So a save that
                    // toggles nothing writes nothing (#1007). Ids whose resulting bitmask agrees are written in one PUT.
                    val baselines = store.permissionsFor(ids)
                    val touched = current.touched.filter { it in offered }.toSet()
                    val results =
                        ids
                            .associateWith { id ->
                                val baseline = baselines[id] ?: 0
                                val kept = ManageablePermission.decode(baseline) - touched
                                ManageablePermission.apply(baseline, kept + current.selected.filter { it in touched })
                            }.filter { (id, permissions) -> isOwner || mayChangeAsNonOwner(id, permissions) }
                    results
                        .filter { (id, permissions) -> permissions != (baselines[id] ?: 0) }
                        .entries
                        .groupBy({ it.value }, { it.key })
                        .forEach { (permissions, groupIds) ->
                            connection.api().bulkUpdateUsers(SeerrBulkUsersBody(ids = groupIds, permissions = permissions))
                            store.updatePermissions(groupIds, permissions)
                        }
                    results.size
                }.onSuccess { saved ->
                    edit.value = null
                    selection.value = emptySet()
                    eventFlow.emit(UsersEvent.PermissionsSaved(saved))
                }.onFailure { failure ->
                    edit.update { it?.copy(saving = false) }
                    eventFlow.emit(UsersEvent.Failed(failure.toSeerrError()))
                }
            }
        }
    }
