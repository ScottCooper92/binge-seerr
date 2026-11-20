package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.companion.sdk.attempt
import com.binge.designsystem.component.BingeConfirmDialog
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.operationFailed
import com.cooper.homelab.core.telemetry.Analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One thing a page does about its title's place on the blocklist; a view model takes it in one function. */
enum class BlocklistStep { Ask, Cancel, Block, Unblock }

/** What a page does about blocking its title: ask first, then block, or leave it be. */
class TitleBlock(
    val onAsk: () -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
)

/** The confirm over a page that Block opens. Blocking stops the title being requested by anyone, so it is asked, and says how to undo it. */
@Composable
internal fun TitleBlockConfirm(
    title: String,
    block: TitleBlock,
) {
    BingeConfirmDialog(
        title = stringResource(R.string.title_block_confirm_title, title),
        message = stringResource(R.string.title_block_confirm_message),
        confirmLabel = stringResource(R.string.title_page_action_block),
        destructive = true,
        onConfirm = block.onConfirm,
        onDismissRequest = block.onCancel,
    )
}

/**
 * Sends a block or an unblock for a page's view model: counts it when it went through and reports it as a failed operation when it
 * did not, then hands the outcome back. [serverProfile] is read only for the failure's report.
 */
internal fun CoroutineScope.sendBlocklistChange(
    analytics: Analytics,
    action: String,
    operation: String,
    serverProfile: suspend () -> SeerrServerProfile?,
    send: suspend () -> Unit,
    onDone: () -> Unit,
    onFailed: (SeerrError) -> Unit,
) {
    launch {
        attempt { send() }.fold(
            onSuccess = {
                analytics.event(AnalyticsEvents.BLOCKLIST_CHANGED, mapOf(AnalyticsEvents.PARAM_ACTION to action))
                onDone()
            },
            onFailure = {
                analytics.operationFailed(operation, it, serverProfile())
                onFailed(it.toSeerrError())
            },
        )
    }
}
