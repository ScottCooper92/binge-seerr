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
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrBulkUsersBody
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the browser needs once per connection: which toggles the server offers, and where a row opens. */
private data class UsersScope(
    val jellyseerrLineage: Boolean = false,
    val baseUrl: String? = null,
)

/**
 * The users browser: one cached, sorted list read from the cache and refreshed through the
 * mediator, and the bulk edit, which writes one permission set to every selected user and moves
 * their cached rows with it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
@HiltViewModel
class UsersViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val store: UserStore,
    ) : ViewModel() {
        private val selectedSort = MutableStateFlow(UserSort.Created)
        private val selection = MutableStateFlow<Set<Int>>(emptySet())
        private val edit = MutableStateFlow<BulkEdit?>(null)

        private val eventFlow = MutableSharedFlow<UsersEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<UsersEvent> = eventFlow.asSharedFlow()

        private val scope: Flow<UsersScope> =
            flow {
                val profile = runCatching { connection.profile() }.getOrNull()
                val baseUrl = runCatching { connection.current().baseUrl }.getOrNull()
                emit(UsersScope(jellyseerrLineage = profile?.hasBlocklist == true, baseUrl = baseUrl))
            }.stateIn(viewModelScope, SharingStarted.Lazily, UsersScope())

        val users: Flow<PagingData<UserItem>> =
            selectedSort
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
            combine(selectedSort, selection, edit, scope) { sort, selection, edit, scope ->
                UsersUiState.Ready(
                    sort = sort,
                    selection = selection,
                    edit = edit,
                    offered = ManageablePermission.offered(scope.jellyseerrLineage),
                    baseUrl = scope.baseUrl,
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, UsersUiState.Loading)

        fun setSort(sort: UserSort) {
            selectedSort.value = sort
        }

        fun toggleSelected(userId: Int) = selection.update { if (userId in it) it - userId else it + userId }

        fun clearSelection() {
            selection.value = emptySet()
        }

        /** Opens the editor on nothing granted: the set written is the whole new set for everyone selected. */
        fun startBulkEdit() {
            if (selection.value.isEmpty() || edit.value != null) return
            edit.value = BulkEdit()
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
            val permissions = ManageablePermission.apply(0, current.selected)
            viewModelScope.launch {
                runCatching {
                    connection.api().bulkUpdateUsers(SeerrBulkUsersBody(ids = ids, permissions = permissions))
                    store.updatePermissions(ids, permissions)
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
