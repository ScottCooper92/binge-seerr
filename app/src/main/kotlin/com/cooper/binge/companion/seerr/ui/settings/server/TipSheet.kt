package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeNumberSlider
import com.binge.designsystem.component.BingeSheetKind
import com.cooper.binge.companion.seerr.R
import com.binge.designsystem.R as DesR

/** PROTOTYPE: pounds the slider offers; each whole pound will be one Play product (tip_1 .. tip_20). */
private val TIP_RANGE = 1..20
private const val DEFAULT_TIP = 5

/**
 * PROTOTYPE: the tip jar's sheet. The price is a placeholder and nothing is charged; the real
 * amounts are Play Billing products, one per step, with their prices read back from Play.
 */
@Composable
internal fun TipSheet(onDismiss: () -> Unit) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        TipSheetContent(onGive = { onDismiss() })
    }
}

@Composable
internal fun TipSheetContent(
    onGive: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by remember { mutableIntStateOf(DEFAULT_TIP) }
    val resources = LocalContext.current.resources
    val price = { pounds: Int -> resources.getString(R.string.tip_price_placeholder, pounds) }
    Column(
        modifier = modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.tip_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.tip_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BingeNumberSlider(
            value = amount,
            onValueChange = { amount = it ?: DEFAULT_TIP },
            range = TIP_RANGE,
            format = price,
            description = stringResource(R.string.tip_amount),
        )
        BingeFilledButton(
            label = stringResource(R.string.tip_give, price(amount)),
            onClick = { onGive(amount) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
