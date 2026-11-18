package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cooper.binge.companion.seerr.review.ReviewPromptViewModel
import com.cooper.homelab.core.uiandroid.review.ReviewPromptOnReturnToRoot as SharedReviewPromptOnReturnToRoot

/** The shared return-to-root review ask, with the root being a back stack of one or none. */
@Composable
internal fun ReviewPromptOnReturnToRoot(
    connected: Boolean?,
    stackDepth: Int,
    viewModel: ReviewPromptViewModel = hiltViewModel(),
) = SharedReviewPromptOnReturnToRoot(connected = connected, atRoot = stackDepth <= 1, reviewPrompt = viewModel.prompt)
