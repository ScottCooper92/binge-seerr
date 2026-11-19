package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics

/** How far a large bar has collapsed before its title-row account gives way to the collapsed bar's copy. */
internal const val ACCOUNT_HANDOVER_COLLAPSE = 0.5f

/**
 * The account action in a large bar: [HubAccountAction] in the title row (full) or in the collapsed bar (fading in with [alpha]).
 * Only the copy that shows takes taps and is announced, so the other is neither tapped by accident nor read twice.
 */
@Composable
internal fun TitleBarAccount(
    bar: AccountBar,
    enabled: Boolean,
    onOpen: (accountId: Int) -> Unit,
    alpha: Float = 1f,
) = HubAccountAction(
    account = bar.account,
    quota = bar.quota,
    onClick = { onOpen(bar.account.id) },
    enabled = enabled,
    modifier = Modifier.alpha(alpha).then(if (enabled) Modifier else Modifier.clearAndSetSemantics {}),
)
