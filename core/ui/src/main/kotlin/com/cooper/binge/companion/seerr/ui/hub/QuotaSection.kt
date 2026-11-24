package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.core.ui.R
import com.cooper.binge.companion.seerr.core.ui.R as UiR

/** Request quota as settings-style rows: Movies and TV, each with what is left, or "Unlimited" and no bar. */
@Composable
fun QuotaSection(
    quota: HubQuota,
    modifier: Modifier = Modifier,
) {
    ItemGroup(title = stringResource(R.string.hub_quota_title), rows = quotaRows(quota), modifier = modifier)
}

@Composable
private fun quotaRows(quota: HubQuota): List<ListItem> =
    listOf(
        quotaRow(Icons.Filled.Movie, stringResource(R.string.hub_quota_movies), quota.movie),
        quotaRow(Icons.Filled.Tv, stringResource(R.string.hub_quota_tv), quota.tv),
    )

/** One type's quota: "x of y left" and the window when the server reports one, with a usage bar. A null bucket is unlimited. */
@Composable
private fun quotaRow(
    icon: ImageVector,
    label: String,
    bucket: HubQuotaBucket?,
): ListItem =
    ListItem(
        icon = icon,
        label = label,
        detail =
            if (bucket == null) {
                stringResource(R.string.hub_quota_unlimited)
            } else {
                listOfNotNull(
                    pluralStringResource(UiR.plurals.hub_quota_remaining, bucket.remaining, bucket.remaining, bucket.limit),
                    bucket.days?.let { pluralStringResource(UiR.plurals.hub_quota_period, it, it) },
                ).joinToString(stringResource(UiR.string.hub_meta_separator))
            },
        clickable = false,
        trailingContent =
            bucket?.let {
                {
                    LinearProgressIndicator(
                        progress = { it.used.toFloat() / it.limit },
                        modifier = Modifier.width(dimensionResource(R.dimen.hub_quota_bar_width)),
                    )
                }
            },
    )
