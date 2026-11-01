package com.cooper.binge.companion.seerr.util

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.isFocusable

/**
 * A focusable that holds no focusable of its own: the control itself, not a container around it. A matcher that looks
 * for a label in a node's descendants would otherwise find the sheet or page that wraps the control too.
 */
fun isFocusableLeaf(): SemanticsMatcher = isFocusable() and !hasAnyDescendant(isFocusable())
