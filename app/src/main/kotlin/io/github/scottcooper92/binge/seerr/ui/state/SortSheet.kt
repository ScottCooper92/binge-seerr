package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeChoiceSheetContent
import io.github.scottcooper92.binge.seerr.R

/**
 * A single-select picker over any list, on the design system's choice sheet, the same sheet the settings rows open:
 * picking one applies it and dismisses. [title] defaults to the sort picker's own copy, its first and still most
 * common caller, so a differently-titled caller (`ChoiceRow`) is the only one that has to pass one.
 */
@Composable
fun <T> SortSheet(
    choices: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.sort_title),
) {
    BingeChoiceSheet(
        title = title,
        choices = BingeChoiceList.Ready(choices.map { BingeChoice(it, label(it)) }),
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

/**
 * The sort or choice list alone, for a caller that is already a sheet and swaps it in as content: the request editor
 * (#336). It is the design system's stateless choice content, so it lists, sections and marks exactly as [SortSheet]
 * does, without a second drag handle under the host sheet's own. It scrolls itself, so its caller must not.
 */
@Composable
fun <T> SortContent(
    choices: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.sort_title),
) {
    BingeChoiceSheetContent(
        title = title,
        choices = BingeChoiceList.Ready(choices.map { BingeChoice(it, label(it)) }),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        dragHandle = false,
    )
}
