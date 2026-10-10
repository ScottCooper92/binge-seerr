package io.github.scottcooper92.binge.seerr.ui.state

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserRole

/**
 * A user's role as a tag, drawn one way wherever a person is named: cased as a role, as [BingeTag] asks, with the
 * owner and an administrator tinted and anyone else neutral. Whether a row shows it for an ordinary user is the row's call.
 */
@Composable
internal fun RoleTag(
    role: UserRole,
    modifier: Modifier = Modifier,
) {
    val sentiment = if (role == UserRole.User) BingeSentiment.Neutral else BingeSentiment.Info
    BingeTag(
        label = stringResource(role.labelRes()),
        modifier = modifier,
        tint = sentiment.accent(),
        fill = sentiment.fill(),
        uppercase = false,
    )
}

@StringRes
internal fun UserRole.labelRes(): Int =
    when (this) {
        UserRole.Owner -> R.string.user_role_owner
        UserRole.Admin -> R.string.hub_role_admin
        UserRole.User -> R.string.hub_role_user
    }
