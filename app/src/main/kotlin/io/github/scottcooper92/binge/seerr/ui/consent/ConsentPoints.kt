package io.github.scottcooper92.binge.seerr.ui.consent

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** One of the three things the consent screen says, shared by the phone and the television. */
internal class ConsentPoint(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
)

internal val ConsentPoints =
    listOf(
        ConsentPoint(Icons.Filled.BarChart, R.string.consent_point_shared_title, R.string.consent_point_shared_detail),
        ConsentPoint(Icons.Filled.VisibilityOff, R.string.consent_point_never_title, R.string.consent_point_never_detail),
        ConsentPoint(Icons.Filled.ToggleOff, R.string.consent_point_change_title, R.string.consent_point_change_detail),
    )

/** The chart with a lock on it that heads the phone's consent screen. Decorative. */
@Composable
internal fun PrivacyHero(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(dimensionResource(R.dimen.consent_hero_size))) {
        Box(
            modifier = Modifier.fillMaxSize().clip(BingeShapes.Hero).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(dimensionResource(R.dimen.consent_hero_icon_size)),
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dimensionResource(R.dimen.consent_hero_badge_size))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(dimensionResource(R.dimen.consent_hero_badge_icon_size)),
            )
        }
    }
}

@Composable
internal fun ConsentPointsCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(BingeShapes.ListCard).background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ConsentPoints.forEachIndexed { index, point ->
            ConsentPointRow(point)
            if (index < ConsentPoints.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ConsentPointRow(point: ConsentPoint) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_sm)),
    ) {
        Box(
            modifier =
                Modifier
                    .size(dimensionResource(R.dimen.consent_point_icon_size))
                    .clip(BingeShapes.Large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = point.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimensionResource(DesR.dimen.icon_size_l)),
            )
        }
        Column {
            Text(
                text = stringResource(point.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(point.detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_xxs)),
            )
        }
    }
}
