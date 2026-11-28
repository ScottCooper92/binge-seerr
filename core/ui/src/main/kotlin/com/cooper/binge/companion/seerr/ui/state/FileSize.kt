package com.cooper.binge.companion.seerr.ui.state

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** `4.2 GB`, as the platform writes a size: its units and its decimal separator, in the app's language. A negative size reads as zero. */
@Composable
fun formatFileSize(bytes: Long): String = Formatter.formatFileSize(LocalContext.current, bytes.coerceAtLeast(0))
