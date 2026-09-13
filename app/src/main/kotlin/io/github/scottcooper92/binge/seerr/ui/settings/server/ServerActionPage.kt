package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTopBar
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEventSnackbarEffect
import kotlinx.coroutines.flow.Flow

/** The frame of a server page whose actions are immediate rather than a draft: a title, and a snackbar for each outcome. */
@Composable
internal fun ServerActionPage(
    title: String,
    events: Flow<EditorEvent>,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    EditorEventSnackbarEffect(events, snackbarHostState)
    Scaffold(snackbarHost = { BingeSnackbarHost(snackbarHostState) }, topBar = { BingeTopBar(title = title, onBack = onBack) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), content = content)
    }
}
