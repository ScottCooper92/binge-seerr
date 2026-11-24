package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.core.ui.R
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.ui.state.formatFileSize

/** A quality profile's name in a picker, marked where it is the one a plain request to its server gets. */
@Composable
fun profileOptionLabel(
    name: String,
    isDefault: Boolean,
): String = if (isDefault) stringResource(R.string.request_option_default_suffix, name) else name

/** A root folder's path in a picker, with the room the server reports left on it where it reported any. */
@Composable
fun folderOptionLabel(
    path: String,
    freeBytes: Long?,
): String = freeBytes?.let { stringResource(R.string.request_folder_free_space, path, formatFileSize(it)) } ?: path

@Composable
fun RequestChoices.profileLabel(
    profile: RequestChoice,
    anime: Boolean,
): String = profileOptionLabel(profile.label, profile.id == defaultProfileFor(anime))

@Composable
fun RequestChoices.folderLabel(path: String): String = folderOptionLabel(path, freeSpace[path])

/** The names of the chosen tags for the Tags row, or "None". */
@Composable
fun RequestChoices.tagsSummary(selected: Set<Int>): String =
    tags.filter { it.id in selected }.joinToString(", ") { it.label }.ifEmpty { stringResource(R.string.request_tags_none) }
