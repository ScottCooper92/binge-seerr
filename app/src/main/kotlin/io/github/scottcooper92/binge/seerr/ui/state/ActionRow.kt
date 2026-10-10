package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.R as DesR

/**
 * A row of an action sheet that does something where it is, so it has no chevron. A [destructive] one takes the error
 * colour, as the request actions sheet's deletes do, and a row that is not [enabled] is dimmed and inert.
 */
@Composable
internal fun actionItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): ListItem =
    ListItem(
        icon = icon,
        iconTint = if (destructive) MaterialTheme.colorScheme.error else null,
        label = label,
        destination = ListItemDestination.Action,
        disabled = !enabled,
        onClick = onClick,
    )

/** A row that leaves the app for a web page. The design system marks it as leaving, for a screen reader too. */
internal fun externalItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
): ListItem = ListItem(icon = icon, label = label, destination = ListItemDestination.External, onClick = onClick)

/** The body of an action or link sheet: its [rows] as one group, inset as the request actions sheet's are. */
@Composable
internal fun ActionSheetGroup(
    rows: List<ListItem>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    ItemGroup(
        title = title,
        rows = rows,
        modifier =
            modifier
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
    )
}
