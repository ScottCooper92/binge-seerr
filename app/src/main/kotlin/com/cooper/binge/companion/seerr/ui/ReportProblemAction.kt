package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.component.BingeBarIconAction
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

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
            ReportProblemMenuItem(
                onClick = {
                    expanded = false
                    onReportProblem()
                },
            )
        }
    }
}

/**
 * The item itself: it opens the bug form in the browser, so it carries the open-in-new mark and says so to a screen reader (#2347),
 * and it is a row of its own so a frame can show it.
 */
@Composable
internal fun ReportProblemMenuItem(onClick: () -> Unit) {
    val label = stringResource(R.string.report_problem)
    val description = stringResource(DesR.string.cd_link_chip_opens_outside, label)
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
        trailingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    )
}
