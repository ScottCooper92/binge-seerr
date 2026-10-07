package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Gives the screens inside [content] their own view models, which last as long as [content] is shown rather than as
 * long as the activity. The TV shell wraps setup and the connected shell in one each, so disconnecting and signing in
 * again starts both afresh: without it, the hub's view models outlive the connection they were made for and hold the
 * failed state of the session that ended (#789).
 *
 * The store is held by a view model in the parent's store, under [key], so it outlives the composition. A
 * configuration change recreates the activity and disposes the composition without ending the scope, so it is kept
 * then and cleared only when the scope really leaves or the parent is cleared.
 */
@Composable
internal fun TvViewModelScope(
    key: String,
    content: @Composable () -> Unit,
) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner to scope from" }
    val stores = viewModel<ScopedStores>(viewModelStoreOwner = parent, factory = viewModelFactory { initializer { ScopedStores() } })
    val owner = remember(parent, key) { ScopedViewModelStoreOwner(parent, stores.storeFor(key)) }
    val activity = LocalActivity.current
    DisposableEffect(owner) {
        onDispose {
            if (activity?.isChangingConfigurations != true) stores.clear(key)
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** The scopes' stores, kept in the parent's store so a configuration change does not take them with the composition. */
internal class ScopedStores : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun storeFor(key: String): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    fun clear(key: String) {
        stores.remove(key)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/** A store of its own, with the parent's factory and creation extras, so Hilt and saved state work as they would there. */
private class ScopedViewModelStoreOwner(
    private val parent: ViewModelStoreOwner,
    override val viewModelStore: ViewModelStore,
) : ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (parent as HasDefaultViewModelProviderFactory).defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = (parent as HasDefaultViewModelProviderFactory).defaultViewModelCreationExtras
}
