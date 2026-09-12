package io.github.scottcooper92.binge.seerr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.theme.BingeExpressiveTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.scottcooper92.binge.seerr.ui.SeerrNavHost
import io.github.scottcooper92.binge.seerr.ui.SetupRoute

/**
 * The app's own UI, one Navigation 3 host wearing Binge's theme so the two read as one product.
 * Setup is the start destination; the screens a standalone client needs (#26) push above it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BingeExpressiveTheme {
                SeerrNavHost(backStack = rememberNavBackStack(SetupRoute))
            }
        }
    }
}
