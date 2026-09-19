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
import io.github.scottcooper92.binge.seerr.data.USERS_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.data.UsersRemoteMediator
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrBulkUsersBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the browser needs once per connection: which toggles the server offers, and what the viewer may add. */
private data class UsersScope(
    val jellyseerrLineage: Boolean = false,
    val canAdmit: Boolean = false,
    val importSource: UserOrigin? = null,
    val canGeneratePassword: Boolean = false,
)

/**
 * The users browser: one cached, sorted list read from the cache and refreshed through the
 * mediator, and the bulk edit, which re-applies the chosen toggles onto each selected user's own
 * cached bitmask — never onto a value shared across the selection — and moves their cached rows
 * with it.
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
            UserAdmission(scope = viewModelScope, dispatcher = dispatcher, connection = connection) {
                listVersion.update { it + 1 }
            }

        val events: SharedFlow<UsersEvent> = merge(eventFlow, admission.events).shareIn(viewModelScope, SharingStarted.Lazily)

        /** Bumped when the page becomes visible, so the scope is re-read rather than held from the first visit. */
        private val scopeRefresh = MutableStateFlow(0)

        private val scope: Flow<UsersScope> =
            scopeRefresh
                .flatMapLatest {
                    flow {
                        // The refreshing reads, not the cached ones: both caches live as long as the
                        // connection, so re-running this over them would re-read nothing.
                        val profile = runCatching { connection.refreshProfile() }.getOrNull()
                        val viewer = runCatching { connection.refreshAuthenticatedUser() }.getOrNull()
                        val settings = profile?.settings
                        emit(
                            UsersScope(
                                jellyseerrLineage = profile?.hasBlocklist == true,
                                canAdmit = viewer.toPermissions().canManageUsers,
                                importSource =
                                    when (profile?.mediaServer) {
                                        SeerrMediaServer.Plex -> UserOrigin.Plex
                                        SeerrMediaServer.Jellyfin -> UserOrigin.Jellyfin
                                        SeerrMediaServer.Emby -> UserOrigin.Emby
                                        SeerrMediaServer.NotConfigured, null -> null
                                    },
                                canGeneratePassword = settings?.emailEnabled == true && !settings.applicationUrl.isNullOrBlank(),
                            ),
                        )
                    }
                }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.Lazily, UsersScope())

        val users: Flow<PagingData<UserItem>> =
            combine(selectedSort, listVersion) { sort, _ -> sort }
                .flatMapLatest { sort ->
                    Pager(
                        config = PagingConfig(pageSize = USERS_PAGE_SIZE),
                        remoteMediator =
                            UsersRemoteMediator(sort = sort.apiValue, api = connection::api, store = store) { dto, key, index ->
                                dto.toUserItem()?.toEntity(key, index)
                            },
                    ) { store.pagingSource(sort.apiValue) }.flow
                }.map { data -> data.map { it.toUserItem() } }
                .cachedIn(viewModelScope)

        val uiState: StateFlow<UsersUiState> =
            combine(selectedSort, selection, edit, scope, admission.state) { sort, selection, edit, scope, admission ->
                UsersUiState.Ready(
                    sort = sort,
                    selection = selection,
                    edit = edit,
                    offered = ManageablePermission.offered(scope.jellyseerrLineage),
                    canAdmit = scope.canAdmit,
                    importSource = scope.importSource,
                    canGeneratePassword = scope.canGeneratePassword,
                    admission = admission,
                )
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

        fun setSort(sort: UserSort) {
            selectedSort.value = sort
        }

        fun toggleSelected(userId: Int) = selection.update { if (userId in it) it - userId else it + userId }

        fun clearSelection() {
            selection.value = emptySet()
        }

        /**
         * Opens the editor seeded from what the selection already has — the union of every selected
         * user's decoded permissions — so a save that re-ticks nothing still preserves them, rather
         * than opening blank and writing an empty set over whatever they had.
         */
        fun startBulkEdit() {
            val ids = selection.value.toList()
            if (ids.isEmpty() || edit.value != null) return
            edit.value = BulkEdit(saving = true)
            viewModelScope.launch(dispatcher) {
                val selected =
                    store.permissionsFor(ids).values.fold(emptySet<ManageablePermission>()) { acc, bitmask ->
                        acc + ManageablePermission.decode(bitmask)
                    }
                edit.value = BulkEdit(selected = selected)
            }
        }

        fun togglePermission(permission: ManageablePermission) =
            edit.update { current ->
                current?.takeUnless { it.saving }?.let {
                    it.copy(
                        selected =
                            if (permission in
                                it.selected
                            ) {
                                it.selected - permission
                            } else {
                                it.selected + permission
                            },
                    )
                }
                    ?: current
            }

        fun cancelBulkEdit() {
            if (edit.value?.saving != true) edit.value = null
        }

        fun applyBulkEdit() {
            val current = edit.value ?: return
            val ids = selection.value.toList()
            if (current.saving || ids.isEmpty()) return
            edit.value = current.copy(saving = true)
            viewModelScope.launch(dispatcher) {
                runCatching {
                    // Each id's own cached bitmask is the baseline for that id alone, so an unmanaged
                    // bit only some of the selection holds is never carried onto the rest. Ids whose
                    // resulting bitmask agrees are still written together in one PUT.
                    val baselines = store.permissionsFor(ids)
                    val idsByResult = ids.groupBy { id -> ManageablePermission.apply(baselines[id] ?: 0, current.selected) }
                    idsByResult.forEach { (permissions, groupIds) ->
                        connection.api().bulkUpdateUsers(SeerrBulkUsersBody(ids = groupIds, permissions = permissions))
                        store.updatePermissions(groupIds, permissions)
                    }
                }.onSuccess {
                    edit.value = null
                    selection.value = emptySet()
                    eventFlow.emit(UsersEvent.PermissionsSaved(ids.size))
                }.onFailure { failure ->
                    edit.update { it?.copy(saving = false) }
                    eventFlow.emit(UsersEvent.Failed(failure.toSeerrError()))
                }
            }
        }
    }
