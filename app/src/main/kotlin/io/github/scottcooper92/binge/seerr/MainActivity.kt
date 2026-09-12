package io.github.scottcooper92.binge.seerr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.theme.BingeExpressiveTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import io.github.scottcooper92.binge.seerr.ui.SeerrNavHost

/**
 * The app's own UI, one Navigation 3 host wearing Binge's theme so the two read as one product.
 * Home is the start destination, setup or the hub by whether a server is saved; the sections push above it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BingeExpressiveTheme {
                SeerrNavHost(backStack = rememberNavBackStack(HomeRoute))
            }
        }
    }
}
