package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill

/**
 * PROTOTYPE: the signed-in account as a top-bar action. The avatar opens the user's own page; where the server limits
 * requests, a ring around it fills as the tighter of the movie and TV quotas is used: green, then yellow, then red.
 * Unlimited is a full green ring.
 */
@Composable
internal fun HubAccountAction(
    account: HubAccount,
    quota: HubQuota?,
    onClick: () -> Unit,
) {
    // PROTOTYPE: the ring fills as the tighter quota is used, green to yellow to red; unlimited is a full green ring.
    val tightest = quota?.let { listOfNotNull(it.movie, it.tv) }?.maxByOrNull { it.used.toFloat() / it.limit.coerceAtLeast(1) }
    val used = tightest?.let { it.used.toFloat() / it.limit.coerceAtLeast(1) }
    val ringColor =
        when {
            used == null || used < 0.5f -> BingeSentiment.Positive.fill()
            used < 0.8f -> BingeSentiment.Caution.fill()
            else -> BingeSentiment.Negative.fill()
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = account.name },
    ) {
        CircularProgressIndicator(
            progress = { used ?: 1f },
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp,
            color = ringColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        BingeInitialsAvatar(name = account.name, avatarUrl = account.avatarUrl, size = 32.dp)
    }
}
