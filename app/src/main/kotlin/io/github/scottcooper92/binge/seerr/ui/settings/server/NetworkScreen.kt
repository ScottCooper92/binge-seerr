package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemConnector
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.AddressKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.GroupMessage
import io.github.scottcooper92.binge.seerr.ui.users.settings.NumberKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.VerbatimKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import kotlinx.coroutines.flow.Flow

/**
 * The network page in the web client's order, as groups of list rows: the switches every lineage has, then the DNS
 * cache and the outbound proxy where the server sent them. Each of those two is a switch, and only while it is on do
 * its settings hang beneath it, as the web client shows them. Every value is checked in the sheet that edits it.
 */
@Composable
fun NetworkScreen(
    state: EditorUiState<NetworkForm>,
    events: Flow<EditorEvent>,
    actions: EditorActions<NetworkForm>,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_network),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ItemGroup(
            title = stringResource(R.string.settings_group_general),
            rows =
                listOfNotNull(
                    editorToggle(
                        Icons.Filled.Shield,
                        stringResource(R.string.server_settings_trust_proxy),
                        draft.trustProxy,
                        enabled,
                    ) { on ->
                        actions.onEdit { it.copy(trustProxy = on) }
                    },
                    editorToggle(Icons.Filled.Lock, stringResource(R.string.server_settings_csrf), draft.csrfProtection, enabled) { on ->
                        actions.onEdit { it.copy(csrfProtection = on) }
                    },
                    draft.forceIpv4First?.let { prefer ->
                        editorToggle(Icons.Filled.Public, stringResource(R.string.server_settings_force_ipv4), prefer, enabled) { on ->
                            actions.onEdit { it.copy(forceIpv4First = on) }
                        }
                    },
                ),
        )
        draft.dnsCache?.let { cache ->
            DnsCacheGroup(cache, enabled) { transform -> actions.onEdit { it.copy(dnsCache = it.dnsCache?.let(transform)) } }
        }
        draft.proxy?.let { proxy ->
            ProxyGroup(proxy, enabled) { transform -> actions.onEdit { it.copy(proxy = it.proxy?.let(transform)) } }
        }
    }
}

@Composable
private fun DnsCacheGroup(
    cache: DnsCacheForm,
    enabled: Boolean,
    onEdit: ((DnsCacheForm) -> DnsCacheForm) -> Unit,
) {
    val ttlError = stringResource(R.string.server_settings_dns_ttl_hint)
    val orderError = stringResource(R.string.server_settings_dns_ttl_order)
    val own = stringResource(R.string.server_settings_dns_ttl_default)
    val bounds =
        listOf(
            textSettingItem(
                icon = Icons.Filled.Timer,
                label = stringResource(R.string.server_settings_dns_min_ttl),
                value = cache.minTtl,
                enabled = enabled,
                onChange = { value -> onEdit { it.copy(minTtl = value) } },
                keyboard = NumberKeyboard,
                emptyLabel = own,
                hint = ttlError,
                check = { value ->
                    when {
                        !value.isTtl() -> ttlError
                        !cache.copy(minTtl = value).orderValid -> orderError
                        else -> null
                    }
                },
            ),
            textSettingItem(
                icon = Icons.Filled.HourglassFull,
                label = stringResource(R.string.server_settings_dns_max_ttl),
                value = cache.maxTtl,
                enabled = enabled,
                onChange = { value -> onEdit { it.copy(maxTtl = value) } },
                keyboard = NumberKeyboard,
                emptyLabel = own,
                hint = ttlError,
                check = { value ->
                    when {
                        !value.isTtl() -> ttlError
                        !cache.copy(maxTtl = value).orderValid -> orderError
                        else -> null
                    }
                },
            ),
        )
    ItemGroup(
        title = stringResource(R.string.server_settings_dns_cache),
        rows =
            listOf(
                editorToggle(
                    Icons.Filled.Storage,
                    stringResource(R.string.server_settings_dns_cache_enabled),
                    cache.enabled,
                    enabled,
                ) { on ->
                    onEdit { it.copy(enabled = on) }
                },
            ) + if (cache.enabled) bounds.joined() else emptyList(),
    )
}

@Composable
private fun ProxyGroup(
    proxy: ProxyForm,
    enabled: Boolean,
    onEdit: ((ProxyForm) -> ProxyForm) -> Unit,
) {
    val settings = proxySettingRows(proxy, enabled, onEdit)
    val credentials =
        when {
            !proxy.enabled -> null
            proxy.userMissing -> R.string.server_settings_proxy_user_missing
            proxy.passwordMissing -> R.string.server_settings_proxy_password_missing
            else -> null
        }
    ItemGroup(
        title = stringResource(R.string.server_settings_proxy),
        rows =
            listOf(
                editorToggle(
                    Icons.Filled.PowerSettingsNew,
                    stringResource(R.string.server_settings_proxy_enabled),
                    proxy.enabled,
                    enabled,
                ) { on ->
                    onEdit { it.copy(enabled = on) }
                },
            ) + if (proxy.enabled) settings.joined() else emptyList(),
        belowRows = credentials?.let { message -> { GroupMessage(stringResource(message), error = true) } },
    )
}

/** The proxy's settings, in the web client's order, for while it is on. */
@Composable
private fun proxySettingRows(
    proxy: ProxyForm,
    enabled: Boolean,
    onEdit: ((ProxyForm) -> ProxyForm) -> Unit,
): List<ListItem> {
    val requiredError = stringResource(R.string.editor_field_required)
    val portError = stringResource(R.string.editor_error_port)
    return listOf(
        textSettingItem(
            icon = Icons.Filled.Dns,
            label = stringResource(R.string.server_settings_host),
            value = proxy.host,
            enabled = enabled,
            onChange = { value -> onEdit { it.copy(host = value) } },
            keyboard = AddressKeyboard,
            required = true,
            placeholder = stringResource(R.string.placeholder_proxy_host),
            check = { value -> requiredError.takeIf { value.isBlank() } },
        ),
        textSettingItem(
            icon = Icons.Filled.Tag,
            label = stringResource(R.string.server_settings_port),
            value = proxy.port,
            enabled = enabled,
            onChange = { value -> onEdit { it.copy(port = value) } },
            keyboard = NumberKeyboard,
            required = true,
            placeholder = stringResource(R.string.placeholder_port_proxy),
            check = { value -> portError.takeIf { !portValid(value) } },
        ),
        editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), proxy.useSsl, enabled) { on ->
            onEdit { it.copy(useSsl = on) }
        },
        textSettingItem(
            icon = Icons.Filled.Person,
            label = stringResource(R.string.server_settings_agent_username),
            value = proxy.user,
            enabled = enabled,
            onChange = { value -> onEdit { it.copy(user = value) } },
            keyboard = VerbatimKeyboard,
        ),
        textSettingItem(
            icon = Icons.Filled.Key,
            label = stringResource(R.string.server_settings_agent_password),
            value = proxy.password,
            enabled = enabled,
            onChange = { value -> onEdit { it.copy(password = value) } },
            shown =
                stringResource(
                    if (proxy.password.isEmpty()) R.string.settings_value_not_set else R.string.server_settings_secret_set,
                ),
            secret = true,
        ),
        textSettingItem(
            icon = Icons.Filled.Block,
            label = stringResource(R.string.server_settings_proxy_bypass),
            value = proxy.bypassFilter,
            enabled = enabled,
            onChange = { value -> onEdit { it.copy(bypassFilter = value) } },
            keyboard = AddressKeyboard,
            hint = stringResource(R.string.server_settings_proxy_bypass_hint),
        ),
        editorToggle(
            Icons.Filled.Speed,
            stringResource(R.string.server_settings_proxy_bypass_local),
            proxy.bypassLocalAddresses,
            enabled,
        ) { on ->
            onEdit { it.copy(bypassLocalAddresses = on) }
        },
    )
}

/** Rows that hang beneath the switch above them, joined to it by the design system's connector. */
private fun List<ListItem>.joined(): List<ListItem> =
    mapIndexed { index, row -> row.copy(connector = if (index == lastIndex) ListItemConnector.End else ListItemConnector.Continue) }
