package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Gives the screens inside [content] their own view models, which last as long as [content] is shown rather than as
 * long as the activity. The TV shell wraps setup and the connected shell in one each, so disconnecting and signing in
 * again starts both afresh: without it, the hub's view models outlive the connection they were made for and hold the
 * failed state of the session that ended (#789).
 */
@Composable
internal fun TvViewModelScope(content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner to scope from" }
    val owner = remember(parent) { ScopedViewModelStoreOwner(parent) }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** A store of its own, with the parent's factory and creation extras, so Hilt and saved state work as they would there. */
private class ScopedViewModelStoreOwner(
    private val parent: ViewModelStoreOwner,
) : ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (parent as HasDefaultViewModelProviderFactory).defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = (parent as HasDefaultViewModelProviderFactory).defaultViewModelCreationExtras
}
