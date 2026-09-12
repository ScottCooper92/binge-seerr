package io.github.scottcooper92.binge.seerr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.SetupScreen
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel

/**
 * The companion's own UI: connect a server, see what is connected, disconnect. Everything else a
 * user does with Seerr happens in Binge, through the exported Service — this app is the
 * credentials' home, not a second client.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: SetupViewModel by viewModels {
        SetupViewModel.Factory((application as SeerrApp).connection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SetupScreen(
                    state = state,
                    onEdit = viewModel::edit,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                )
            }
        }
    }
}
