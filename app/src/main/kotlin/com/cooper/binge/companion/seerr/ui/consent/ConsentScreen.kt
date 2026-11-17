package com.cooper.binge.companion.seerr.ui.consent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.homelab.core.uiandroid.consent.ConsentGate as SharedConsentGate

/** Shows [content] once usage data and crash reports have an answer, and the shared question until then. */
@Composable
fun ConsentGate(
    viewModel: ConsentViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    SharedConsentGate(consent = consent, onAnswer = viewModel::answer, content = content)
}
