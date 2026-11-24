package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * The view-model owners of the editors that open a picker page, by key. A picker is its own page, so it asks the editor
 * beneath it for the editor's own view model and writes its pick straight into the draft: nothing is passed back.
 */
internal class EditorOwners {
    private val owners = mutableMapOf<String, ViewModelStoreOwner>()

    fun register(
        key: String,
        owner: ViewModelStoreOwner,
    ) {
        owners[key] = owner
    }

    operator fun get(key: String): ViewModelStoreOwner? = owners[key]
}

internal val LocalEditorOwners = compositionLocalOf { EditorOwners() }

/** Registers this editor entry's owner under [key] while it composes, so a picker above it finds the same view model. */
@Composable
internal fun RegisterEditorOwner(key: String) {
    val owners = LocalEditorOwners.current
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "An editor entry has a view-model owner" }
    remember(owners, key, owner) { owners.register(key, owner).let { owner } }
}

internal fun sliderOwnerKey(id: Int?) = "slider:$id"

internal fun ruleOwnerKey(id: Int?) = "rule:$id"
