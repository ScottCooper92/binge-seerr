package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.SavedStateHandle
import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private const val KEY = "editor.draft"

private val json = Json { ignoreUnknownKeys = true }

/**
 * Where an explicit-save editor keeps the draft it has not saved, so a long form survives the process being killed in
 * the background (#1026). Rotation needs none of this: the view model outlives it. A page that saves as it changes has
 * no unsaved draft to keep, so it takes none.
 *
 * The draft goes into the page's [SavedStateHandle] as JSON. The system keeps saved state outside the app, so a key, a
 * password or a token is [scrub]bed out first, and [restore] puts it back from the record read on the way back in.
 */
class SavedDraft<T>(
    private val handle: SavedStateHandle,
    private val serializer: KSerializer<T>,
    private val scrub: (T) -> T = { it },
    private val restore: (kept: T, loaded: T) -> T = { kept, _ -> kept },
) {
    /** Keeps [draft], or forgets the kept one when [draft] is null: nothing is unsaved. */
    fun keep(draft: T?) {
        if (draft == null) {
            handle.remove<String>(KEY)
        } else {
            handle[KEY] = json.encodeToString(serializer, scrub(draft))
        }
    }

    /** The kept draft over [loaded], the record just read; null when none was kept, or it no longer reads. */
    fun restoreOver(loaded: T): T? =
        handle
            .get<String>(KEY)
            ?.let { kept -> attempt { json.decodeFromString(serializer, kept) }.getOrNull() }
            ?.let { restore(it, loaded) }
}
