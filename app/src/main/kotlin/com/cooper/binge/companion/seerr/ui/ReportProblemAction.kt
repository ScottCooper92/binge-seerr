package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import com.binge.designsystem.component.BingeBarIconAction
import com.cooper.binge.companion.seerr.R

/**
 * The overflow menu on the setup screens a user sees before a server is connected, where Settings is out of reach. One item
 * for now: **Report a problem**, which opens the same prefilled bug form as Settings, with the app and the device and no server.
 */
@Composable
internal fun ReportProblemAction(
    glassBackgroundAlpha: Float,
    onReportProblem: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val more = stringResource(R.string.overflow_menu)
    Box {
        BingeBarIconAction(Icons.Filled.MoreVert, more, { expanded = true }, glassBackgroundAlpha = glassBackgroundAlpha)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.report_problem)) },
                leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                onClick = {
                    expanded = false
                    onReportProblem()
                },
            )
        }
    }
}
