package com.cooper.binge.companion.seerr.util

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.fail

/**
 * Fails when any text on screen is cropped: a line the layout cut with an ellipsis, or text laid out larger than the box
 * that holds it, so the reader cannot read what the screen set out to say. Not `hasVisualOverflow`, which is also true
 * for text that only runs past its own measured size and so cannot tell a cut line from a tight fit; this compares the
 * text's own layout with its parent's bounds. [what] names the screen in the failure.
 */
fun ComposeContentTestRule.assertNoCroppedText(what: String) {
    waitForIdle()
    val cropped =
        onRoot(useUnmergedTree = true)
            .fetchSemanticsNode()
            .allNodes()
            .mapNotNull { node -> node.croppedText() }
    if (cropped.isNotEmpty()) fail("$what has cropped text: ${cropped.joinToString("; ")}")
}

private fun SemanticsNode.allNodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.allNodes() }

private fun SemanticsNode.croppedText(): String? {
    val layout = layoutResult() ?: return null
    val text = layout.layoutInput.text.text
    val parentSize = parent?.size ?: size
    return when {
        layout.isEllipsized() -> "\"$text\" is cut with an ellipsis"
        layout.size.exceeds(size) || layout.size.exceeds(parentSize) -> "\"$text\" is larger than the box that holds it"
        else -> null
    }
}

private fun SemanticsNode.layoutResult(): TextLayoutResult? {
    if (config.getOrNull(SemanticsProperties.Text) == null) return null
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.firstOrNull()
}

private fun TextLayoutResult.isEllipsized(): Boolean = (0 until lineCount).any { isLineEllipsized(it) }

private fun IntSize.exceeds(box: IntSize): Boolean = width > box.width || height > box.height
