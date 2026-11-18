package com.cooper.binge.companion.seerr.ui.discover.title.credits

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.R

/** A department as the server names it, in the user's language. One this app does not know is shown as the server wrote it. */
@Composable
internal fun String.departmentLabel(): String =
    when (this) {
        "" -> stringResource(R.string.title_credits_department_other)
        "Directing" -> stringResource(R.string.title_credits_department_directing)
        "Writing" -> stringResource(R.string.title_credits_department_writing)
        "Production" -> stringResource(R.string.title_credits_department_production)
        "Camera" -> stringResource(R.string.title_credits_department_camera)
        "Sound" -> stringResource(R.string.title_credits_department_sound)
        "Art" -> stringResource(R.string.title_credits_department_art)
        "Editing" -> stringResource(R.string.title_credits_department_editing)
        "Costume & Make-Up" -> stringResource(R.string.title_credits_department_costume)
        "Visual Effects" -> stringResource(R.string.title_credits_department_visual_effects)
        "Lighting" -> stringResource(R.string.title_credits_department_lighting)
        "Crew" -> stringResource(R.string.title_credits_department_crew)
        "Actors" -> stringResource(R.string.title_credits_department_actors)
        else -> this
    }
