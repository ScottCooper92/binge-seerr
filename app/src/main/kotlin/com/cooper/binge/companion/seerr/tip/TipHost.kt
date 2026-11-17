package com.cooper.binge.companion.seerr.tip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.presentation.tip.TipViewModel
import com.cooper.homelab.core.review.ReviewPrompt
import com.cooper.homelab.core.tip.TipBilling
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Holds the shared tip view model for the page, so it is made once, reads the prices once and survives a rotation. */
@HiltViewModel
internal class TipHost
    @Inject
    constructor(
        billing: TipBilling,
        reviewPrompt: ReviewPrompt,
    ) : ViewModel() {
        val model = TipViewModel(viewModelScope, billing, reviewPrompt)
    }
