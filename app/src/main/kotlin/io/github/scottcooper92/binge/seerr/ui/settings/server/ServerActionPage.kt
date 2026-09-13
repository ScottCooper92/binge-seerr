package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** The frame of a server page whose actions are immediate rather than a draft: a title, and a snackbar for each outcome. */
@Composable
internal fun ServerActionPage(
    title: String,
    events: Flow<EditorEvent>,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                EditorEvent.Saved ->
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.user_settings_saved),
                        SnackbarMessageKind.Confirmation,
                    )
                is EditorEvent.Failed ->
                    snackbarHostState.showSnackbar(
                        resources.getString(event.error.messageRes()),
                        SnackbarMessageKind.Error,
                    )
                is EditorEvent.Notice ->
                    snackbarHostState.showSnackbar(
                        resources.getString(event.messageRes),
                        SnackbarMessageKind.Confirmation,
                    )
            }
        }
    }
    Scaffold(snackbarHost = { BingeSnackbarHost(snackbarHostState) }, topBar = { BingeTopBar(title = title, onBack = onBack) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), content = content)
    }
}
