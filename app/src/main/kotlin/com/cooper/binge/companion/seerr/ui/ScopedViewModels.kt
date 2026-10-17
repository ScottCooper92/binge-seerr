package com.cooper.binge.companion.seerr.ui

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
 * Gives the screens inside [content] view models that last as long as [content] is shown, not the activity.
 * The TV shell wraps setup and the connected shell in one each, so a hub's view models do not outlive their
 * connection and hold its failed state (#789). The sign-in after a rejected session has one too (#810).
 *
 * The store is held by a view model in the parent's store, under [key]. A configuration change disposes
 * the composition without ending the scope, so the store is cleared only when the scope or parent really goes.
 */
@Composable
internal fun ScopedViewModels(
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
