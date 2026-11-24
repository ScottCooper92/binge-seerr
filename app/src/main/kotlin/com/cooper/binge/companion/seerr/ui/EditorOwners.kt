package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * The view-model owners of the editors that open a picker page, by key. A picker is its own page, so it asks the editor
 * beneath it for the editor's own view model and writes its pick straight into the draft: nothing is passed back.
 *
 * It is a view model of the host, so it outlives a rotation as the navigation entries' own stores do, and a picker open
 * across one finds its editor without the editor composing. After the process is killed it is empty, and a restored
 * picker leaves for the editor. An owner is replaced when its editor is opened again, and it is a few bytes until then.
 */
internal class EditorOwners : ViewModel() {
    private val owners = mutableMapOf<String, ViewModelStoreOwner>()

    fun register(
        key: String,
        owner: ViewModelStoreOwner,
    ) {
        owners[key] = owner
    }

    operator fun get(key: String): ViewModelStoreOwner? = owners[key]
}

internal val LocalEditorOwners = compositionLocalOf<EditorOwners> { error("The host provides the editor owners") }

/** Registers this editor entry's owner under [key] once it has composed, so a picker above it finds the same view model. */
@Composable
internal fun RegisterEditorOwner(key: String) {
    val owners = LocalEditorOwners.current
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "An editor entry has a view-model owner" }
    SideEffect { owners.register(key, owner) }
}

internal fun sliderOwnerKey(id: Int?) = "slider:$id"

internal fun ruleOwnerKey(id: Int?) = "rule:$id"
