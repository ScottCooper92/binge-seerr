package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.component.InfoValue
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * One fact row: a leading icon standing in for the label this used to spell out, [label] itself
 * kept as the icon's `contentDescription` so a screen reader still says "Requested by" before the
 * value rather than reading just a name.
 */
private data class Fact(
    val icon: ImageVector,
    val label: String,
    val primary: InfoValue,
    val secondary: String? = null,
)

private fun Fact(
    icon: ImageVector,
    label: String,
    primary: String,
    secondary: String? = null,
): Fact = Fact(icon, label, InfoValue.Plain(primary), secondary)

/**
 * The server's own watch tracking — a title-level read-out, the same on every request against it,
 * so it sits under the synopsis with its own header rather than inside [RequestFacts] below, which
 * is this request's own facts and no other request's.
 */
@Composable
internal fun RequestStats(detail: RequestDetail) {
    val facts = watchFacts(detail)
    if (facts.isEmpty()) return
    SectionHeader(title = stringResource(R.string.request_stats_title))
    FactList(facts)
}

/** Who asked, when, and where it was sent — each row dropped where the server does not say. */
@Composable
internal fun RequestFacts(
    detail: RequestDetail,
    onOpenUser: (Int) -> Unit,
) {
    val item = detail.item
    val facts =
        listOfNotNull(
            Fact(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(R.string.request_requested_by),
                primary =
                    linkedOrPlain(
                        item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                        item.requestedById,
                        detail.viewerId,
                        detail.canManageUsers,
                        onOpenUser,
                    ),
                secondary = formatRelativeOrAbsolute(item.requestedAtMillis),
            ),
            detail.modifiedBy?.let {
                Fact(
                    icon = Icons.Filled.Gavel,
                    label = stringResource(R.string.request_modified_by),
                    primary = linkedOrPlain(it, detail.modifiedById, detail.viewerId, detail.canManageUsers, onOpenUser),
                    secondary = detail.updatedAtMillis?.let(::formatRelativeOrAbsolute),
                )
            },
            detail.destination?.serverName?.let { Fact(Icons.Filled.Dns, stringResource(R.string.request_server), it) },
            detail.destination?.profileName?.let { Fact(Icons.Filled.HighQuality, stringResource(R.string.request_profile), it) },
            detail.destination?.rootFolder?.let { Fact(Icons.Filled.Folder, stringResource(R.string.request_root_folder), it) },
            detail.destination?.tags?.takeIf { it.isNotEmpty() }?.let {
                Fact(Icons.Filled.Sell, stringResource(R.string.request_tags), it.joinToString(", "))
            },
        )
    SectionHeader(title = stringResource(R.string.request_this_request_title))
    FactList(facts)
}

@Composable
private fun FactList(facts: List<Fact>) {
    if (facts.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = resolvedContentInset()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        facts.forEach { FactRow(it) }
    }
}

@Composable
private fun FactRow(fact: Fact) {
    val blank =
        when (val primary = fact.primary) {
            is InfoValue.Plain -> primary.text.isBlank()
            is InfoValue.Link -> primary.text.isBlank()
            is InfoValue.Links -> primary.links.all { it.text.isBlank() }
        }
    if (blank) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = fact.icon,
            contentDescription = fact.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(DesR.dimen.detail_stat_icon_size)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (val primary = fact.primary) {
                is InfoValue.Plain ->
                    Text(
                        primary.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                is InfoValue.Link ->
                    Text(
                        primary.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(role = Role.Button, onClick = primary.onClick),
                    )
                is InfoValue.Links ->
                    Text(
                        primary.links.joinToString(", ") { it.text },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
            }
            fact.secondary?.let {
                Text(
                    stringResource(R.string.hub_meta_separator) + it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A name as a link to that user's detail screen where the viewer may actually open it — their own
 * id, or any id at all with `MANAGE_USERS` — plain text otherwise. `SeerrApi.user()` refuses
 * anyone else's id without that permission, so an ungated link would be a dead end rather than a
 * shortcut.
 */
internal fun linkedOrPlain(
    text: String,
    id: Int?,
    viewerId: Int?,
    canManageUsers: Boolean,
    onOpenUser: (Int) -> Unit,
): InfoValue =
    if (id != null && (id == viewerId || canManageUsers)) {
        InfoValue.Link(text, onClick = { onOpenUser(id) })
    } else {
        InfoValue.Plain(text)
    }

/**
 * What the server's own watch tracking says. A read-out rather than an action, so it belongs beside
 * Requested by and Updated rather than inside a sheet of things that change something.
 *
 * The instance is named only where the server holds two, since "12 plays · 4K" on a title with one
 * copy says nothing the row above it has not.
 */
@Composable
private fun watchFacts(detail: RequestDetail): List<Fact> {
    val instances =
        detail.media
            ?.instances
            .orEmpty()
            .filter { it.watch != null }
    val named = instances.size > 1
    return instances.flatMap { instance ->
        val watch = instance.watch ?: return@flatMap emptyList()
        val instanceLabel =
            if (named) stringResource(if (instance.is4k) R.string.settings_service_4k else R.string.media_instance_standard) else null
        listOfNotNull(
            Fact(
                icon = Icons.Filled.PlayCircle,
                label = stringResource(R.string.media_watch_title),
                primary = stringResource(R.string.media_watch_plays, watch.playCount, watch.playCount7Days, watch.playCount30Days),
                secondary = instanceLabel,
            ),
            watch.users.takeIf { it.isNotEmpty() }?.let {
                Fact(
                    icon = Icons.Filled.Groups,
                    label = stringResource(R.string.media_watch_users_label),
                    primary = it.joinToString(stringResource(R.string.hub_meta_separator)),
                    secondary = instanceLabel,
                )
            },
        )
    }
}
