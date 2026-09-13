package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.SetupLinkSheet
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.ui.users.labelRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

class LinkedAccountsActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onLinkPlex: () -> Unit,
    val onUnlinkPlex: () -> Unit,
    val onLinkQuickConnect: () -> Unit,
    val onLinkMediaServer: (username: String, password: String) -> Unit,
    val onUnlinkMediaServer: () -> Unit,
    val onPlexLaunched: () -> Unit,
    val onCancelLink: () -> Unit,
)

/**
 * The linked accounts page: a row per account the server can link, each with its link or
 * unlink. Linking Plex goes through the browser; the media server takes a password or, where
 * the server offers it, Quick Connect. Unlinking is behind a confirm.
 */
@Composable
fun LinkedAccountsScreen(
    state: LinkedAccountsUiState,
    events: Flow<LinkedAccountsEvent>,
    actions: LinkedAccountsActions,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val (message, kind) =
                when (event) {
                    LinkedAccountsEvent.Linked -> R.string.user_settings_linked_done to SnackbarMessageKind.Confirmation
                    LinkedAccountsEvent.Unlinked -> R.string.user_settings_unlinked_done to SnackbarMessageKind.Confirmation
                    LinkedAccountsEvent.LinkExpired -> R.string.setup_error_link_expired to SnackbarMessageKind.Error
                    is LinkedAccountsEvent.Failed -> event.error.messageRes() to SnackbarMessageKind.Error
                }
            snackbarHostState.showSnackbar(resources.getString(message), kind)
        }
    }
    Scaffold(
        snackbarHost = { BingeSnackbarHost(snackbarHostState) },
        topBar = { BingeTopBar(title = stringResource(R.string.user_settings_page_linked), onBack = actions.onBack) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                LinkedAccountsUiState.Loading -> LoadingScreen()
                is LinkedAccountsUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
                is LinkedAccountsUiState.Ready -> LinkedAccountsContent(state, actions)
            }
        }
    }
}

@Composable
private fun LinkedAccountsContent(
    state: LinkedAccountsUiState.Ready,
    actions: LinkedAccountsActions,
) {
    var unlinking by rememberSaveable { mutableStateOf<UserOrigin?>(null) }
    var linkingMediaServer by rememberSaveable { mutableStateOf(false) }
    val rows =
        listOfNotNull(
            accountRow(state.plex, state.busy, onLink = actions.onLinkPlex, onUnlink = { unlinking = UserOrigin.Plex }),
            state.mediaServer?.let { account ->
                accountRow(account, state.busy, onLink = { linkingMediaServer = true }, onUnlink = { unlinking = account.origin })
            },
        )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup(title = null, rows = rows, modifier = Modifier.padding(dimensionResource(DesR.dimen.screen_content_inset)))
    }
    unlinking?.let { origin ->
        BingeConfirmDialog(
            title = stringResource(R.string.user_settings_unlink_title, stringResource(origin.labelRes())),
            message = stringResource(R.string.user_settings_unlink_message),
            confirmLabel = stringResource(R.string.user_settings_unlink),
            destructive = true,
            onConfirm = {
                unlinking = null
                if (origin == UserOrigin.Plex) actions.onUnlinkPlex() else actions.onUnlinkMediaServer()
            },
            onDismiss = { unlinking = null },
        )
    }
    val mediaServer = state.mediaServer
    if (linkingMediaServer && mediaServer != null) {
        MediaServerLinkSheet(
            origin = mediaServer.origin,
            canQuickConnect = state.canQuickConnect,
            onQuickConnect = {
                linkingMediaServer = false
                actions.onLinkQuickConnect()
            },
            onLink = { username, password ->
                linkingMediaServer = false
                actions.onLinkMediaServer(username, password)
            },
            onDismiss = { linkingMediaServer = false },
        )
    }
    state.link?.let { link -> SetupLinkSheet(link, actions.onPlexLaunched, actions.onCancelLink) }
}

@Composable
private fun accountRow(
    account: LinkedAccount,
    busy: Boolean,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
): SettingsRow =
    SettingsRow(
        icon = Icons.Filled.Link,
        label = stringResource(account.origin.labelRes()),
        detail =
            when {
                !account.linked -> stringResource(R.string.user_settings_not_linked)
                account.linkedAs != null -> stringResource(R.string.user_settings_linked_as, account.linkedAs)
                else -> stringResource(R.string.user_settings_linked)
            },
        clickable = false,
        trailingContent = {
            BingeTextButton(
                label = stringResource(if (account.linked) R.string.user_settings_unlink else R.string.user_settings_link),
                onClick = if (account.linked) onUnlink else onLink,
                enabled = !busy,
            )
        },
    )

/** How to link the media server's account: its own credentials, or Quick Connect where the server has it. */
@Composable
private fun MediaServerLinkSheet(
    origin: UserOrigin,
    canQuickConnect: Boolean,
    onQuickConnect: () -> Unit,
    onLink: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(
                stringResource(R.string.user_settings_link_sheet_title, stringResource(origin.labelRes())),
                style = MaterialTheme.typography.titleLarge,
            )
            if (canQuickConnect) {
                BingeOutlinedButton(
                    label = stringResource(R.string.user_settings_link_quick_connect),
                    onClick = onQuickConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EditorTextField(username, stringResource(R.string.setup_username)) { username = it }
            EditorTextField(password, stringResource(R.string.setup_password), secret = true) { password = it }
            BingeFilledButton(
                label = stringResource(R.string.user_settings_link),
                onClick = { onLink(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
