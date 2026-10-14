package com.cooper.binge.companion.seerr.ui.tv.requests

import com.cooper.binge.companion.seerr.ui.tv.TvActionRun

/** These actions, with [mark] run first by those whose success removes their own button: Approve, Decline, Retry and Block. */
internal fun TvRequestDetailActions.markingRefocus(mark: () -> Unit) =
    copy(
        onApprove = {
            mark()
            onApprove()
        },
        onRetryRequest = {
            mark()
            onRetryRequest()
        },
        onDecline = {
            mark()
            onDecline(it)
        },
        onBlock = {
            mark()
            onBlock()
        },
    )

/**
 * These actions, each run through [run] so the page knows what is saving, and whether it failed. Reporting an issue has
 * its own state.
 *
 * A press while the request has an action in flight, [isActing], is dropped. That covers the reload after an action
 * lands too, which the ViewModel would not refuse: the page is still catching up, and a press then would be answered
 * by the reload's events rather than its own. Otherwise the run would wait for an answer that is another action's, such as an edit whose sheet was
 * dismissed while it saved, and report it as this press's.
 */
internal fun TvRequestDetailActions.running(
    run: TvActionRun,
    isActing: Boolean,
) = copy(
    onApprove = { if (!isActing) run.run { onApprove() } },
    onRetryRequest = { if (!isActing) run.run { onRetryRequest() } },
    onDecline = { block -> if (!isActing) run.run { onDecline(block) } },
    onRemove = { block -> if (!isActing) run.run { onRemove(block) } },
    onBlock = { if (!isActing) run.run { onBlock() } },
    onSetMediaStatus = { mediaId, status, is4k -> if (!isActing) run.run { onSetMediaStatus(mediaId, status, is4k) } },
)
