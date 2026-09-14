package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

class UsersActions(
    val onBack: () -> Unit,
    val onSortChange: (UserSort) -> Unit,
    val onOpen: (UserItem) -> Unit,
    val onToggleSelected: (UserItem) -> Unit,
    val onClearSelection: () -> Unit,
    val onStartBulkEdit: () -> Unit,
    val onTogglePermission: (ManageablePermission) -> Unit,
    val onApplyBulkEdit: () -> Unit,
    val onCancelBulkEdit: () -> Unit,
    val admission: UserAdmissionActions,
)

/**
 * The users browser: the sorted, cached list, and a selection mode from a long press whose one
 * action writes a permission set to everyone ticked.
 *
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 */
@Composable
fun UsersScreen(
    state: UsersUiState,
    users: Flow<PagingData<UserItem>>,
    events: Flow<UsersEvent>,
    actions: UsersActions,
    showBack: Boolean = true,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    val ready = state as? UsersUiState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    UsersSnackbarEffect(events, snackbarHostState)
    Scaffold(
        snackbarHost = { BingeSnackbarHost(snackbarHostState) },
        topBar = { UsersTopBar(ready, showBack, actions, onSort = { showSort = true }) },
    ) { padding ->
        if (ready == null) {
            LoadingScreen(Modifier.fillMaxSize().padding(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                UsersBody(
                    lazyItems = users.collectAsLazyPagingItems(),
                    selection = ready.selection,
                    onOpen = actions.onOpen,
                    onToggleSelected = actions.onToggleSelected,
                    // A rejected session cannot be retried past: the hub owns reconnecting.
                    onReconnect = actions.onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    if (showSort && ready != null) {
        SortSheet(
            choices = UserSort.entries,
            selected = ready.sort,
            label = { stringResource(it.labelRes()) },
            onSelect = actions.onSortChange,
            onDismiss = { showSort = false },
        )
    }
    ready?.let { UserAdmissionSheets(state = it, actions = actions.admission) }
    ready?.edit?.let { edit ->
        PermissionsEditorSheet(
            offered = ready.offered,
            edit = edit,
            userCount = ready.selection.size,
            onToggle = actions.onTogglePermission,
            onSave = actions.onApplyBulkEdit,
            onDismiss = actions.onCancelBulkEdit,
        )
    }
}

/** The one snackbar for every outcome this screen can produce, the newest replacing any showing. */
@Composable
private fun UsersSnackbarEffect(
    events: Flow<UsersEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                is UsersEvent.PermissionsSaved ->
                    snackbarHostState.showSnackbar(
                        message = resources.getQuantityString(R.plurals.users_permissions_saved, event.count, event.count),
                        kind = SnackbarMessageKind.Confirmation,
                    )
                is UsersEvent.UserCreated ->
                    snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.users_created, event.name),
                        kind = SnackbarMessageKind.Confirmation,
                    )
                is UsersEvent.UsersImported ->
                    snackbarHostState.showSnackbar(
                        message =
                            if (event.count == 0) {
                                resources.getString(R.string.users_imported_none)
                            } else {
                                resources.getQuantityString(R.plurals.users_imported, event.count, event.count)
                            },
                        kind = SnackbarMessageKind.Confirmation,
                    )
                is UsersEvent.Failed ->
                    snackbarHostState.showSnackbar(
                        message = resources.getString(event.error.messageRes()),
                        kind = SnackbarMessageKind.Error,
                    )
            }
        }
    }
}

/**
 * The bar in both of its modes: the count and the bulk actions while rows are ticked, the screen's
 * own title and its add/sort actions otherwise.
 */
@Composable
private fun UsersTopBar(
    ready: UsersUiState.Ready?,
    showBack: Boolean,
    actions: UsersActions,
    onSort: () -> Unit,
) {
    val selected = ready?.selection?.size ?: 0
    BingeTopBar(
        title =
            if (selected > 0) {
                pluralStringResource(R.plurals.users_selected, selected, selected)
            } else {
                stringResource(R.string.hub_section_users)
            },
        // Selection mode keeps its exit arrow whatever the layout: it leaves a mode, not a screen.
        onBack =
            when {
                selected > 0 -> actions.onClearSelection
                showBack -> actions.onBack
                else -> null
            },
        actions = {
            if (ready == null) return@BingeTopBar
            if (selected > 0) {
                IconButton(onClick = actions.onStartBulkEdit) {
                    Icon(Icons.Filled.ManageAccounts, contentDescription = stringResource(R.string.users_edit_permissions))
                }
                IconButton(onClick = actions.onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.users_clear_selection_cd))
                }
            } else {
                if (ready.canAdmit) {
                    // One way in skips the choice: a server with no media server can only create.
                    val onAdd = if (ready.importSource == null) actions.admission.onStartCreate else actions.admission.onStart
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.users_add_cd))
                    }
                }
                IconButton(onClick = onSort) {
                    Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.requests_sort_cd))
                }
            }
        },
    )
}
