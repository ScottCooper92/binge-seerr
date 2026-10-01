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
import androidx.compose.ui.text.input.KeyboardType
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionCard
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleGroup
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import kotlinx.coroutines.flow.Flow

/** The network page: the switches every lineage has, then the proxy and the DNS cache where the server sent them. */
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
        EditorToggleGroup(
            stringResource(R.string.settings_group_general),
            listOfNotNull(
                editorToggle(
                    Icons.Filled.Shield,
                    stringResource(R.string.server_settings_trust_proxy),
                    draft.trustProxy,
                    enabled,
                ) { value ->
                    actions.onEdit { it.copy(trustProxy = value) }
                },
                editorToggle(Icons.Filled.Lock, stringResource(R.string.server_settings_csrf), draft.csrfProtection, enabled) { value ->
                    actions.onEdit { it.copy(csrfProtection = value) }
                },
                draft.forceIpv4First?.let { on ->
                    editorToggle(Icons.Filled.Public, stringResource(R.string.server_settings_force_ipv4), on, enabled) { value ->
                        actions.onEdit { it.copy(forceIpv4First = value) }
                    }
                },
            ),
        )
        draft.proxy?.let { proxy ->
            ProxyFields(proxy, enabled) { transform -> actions.onEdit { it.copy(proxy = it.proxy?.let(transform)) } }
        }
        draft.dnsCache?.let { cache ->
            DnsCacheFields(cache, enabled) { transform -> actions.onEdit { it.copy(dnsCache = it.dnsCache?.let(transform)) } }
        }
    }
}

@Composable
private fun ProxyFields(
    proxy: ProxyForm,
    enabled: Boolean,
    onEdit: ((ProxyForm) -> ProxyForm) -> Unit,
) {
    EditorSectionCard(stringResource(R.string.server_settings_proxy)) {
        EditorToggleRow(
            editorToggle(
                Icons.Filled.PowerSettingsNew,
                stringResource(R.string.server_settings_proxy_enabled),
                proxy.enabled,
                enabled,
            ) { value ->
                onEdit { it.copy(enabled = value) }
            },
        )
        // The switch is the gate: with the proxy off nothing below it is read, and ProxyForm.valid
        // already says so, so the controls follow rather than looking live over a setting in no use.
        val editable = enabled && proxy.enabled
        EditorTextField(
            proxy.host,
            stringResource(R.string.server_settings_host),
            icon = Icons.Filled.Dns,
            enabled = editable,
            keyboardType = KeyboardType.Uri,
            placeholder = stringResource(R.string.placeholder_proxy_host),
            isError = proxy.enabled && proxy.host.isBlank(),
        ) { value -> onEdit { it.copy(host = value) } }
        EditorTextField(
            proxy.port,
            stringResource(R.string.server_settings_port),
            icon = Icons.Filled.Tag,
            enabled = editable,
            keyboardType = KeyboardType.Number,
            placeholder = stringResource(R.string.placeholder_port_proxy),
            isError = proxy.enabled && !proxy.addressValid && proxy.host.isNotBlank(),
        ) { value -> onEdit { it.copy(port = value) } }
        EditorToggleRow(
            editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), proxy.useSsl, editable) { value ->
                onEdit { it.copy(useSsl = value) }
            },
        )
        EditorTextField(
            proxy.user,
            stringResource(R.string.server_settings_agent_username),
            icon = Icons.Filled.Person,
            enabled = editable,
            autoCorrect = false,
            isError = proxy.enabled && proxy.userMissing,
            supporting = stringResource(R.string.server_settings_proxy_user_missing).takeIf { proxy.enabled && proxy.userMissing },
        ) { value ->
            onEdit {
                it.copy(user = value)
            }
        }
        EditorTextField(
            proxy.password,
            stringResource(R.string.server_settings_agent_password),
            icon = Icons.Filled.Key,
            enabled = editable,
            secret = true,
            isError = proxy.enabled && proxy.passwordMissing,
            supporting =
                stringResource(R.string.server_settings_proxy_password_missing).takeIf { proxy.enabled && proxy.passwordMissing },
        ) { value ->
            onEdit { it.copy(password = value) }
        }
        EditorTextField(
            proxy.bypassFilter,
            stringResource(R.string.server_settings_proxy_bypass),
            icon = Icons.Filled.Block,
            enabled = editable,
            supporting = stringResource(R.string.server_settings_proxy_bypass_hint),
        ) { value -> onEdit { it.copy(bypassFilter = value) } }
        EditorToggleRow(
            editorToggle(
                Icons.Filled.Speed,
                stringResource(R.string.server_settings_proxy_bypass_local),
                proxy.bypassLocalAddresses,
                editable,
            ) { value -> onEdit { it.copy(bypassLocalAddresses = value) } },
        )
    }
}

@Composable
private fun DnsCacheFields(
    cache: DnsCacheForm,
    enabled: Boolean,
    onEdit: ((DnsCacheForm) -> DnsCacheForm) -> Unit,
) {
    EditorSectionCard(stringResource(R.string.server_settings_dns_cache)) {
        EditorToggleRow(
            editorToggle(
                Icons.Filled.Storage,
                stringResource(R.string.server_settings_dns_cache_enabled),
                cache.enabled,
                enabled,
            ) { value ->
                onEdit { it.copy(enabled = value) }
            },
        )
        val editable = enabled && cache.enabled
        EditorTextField(
            cache.minTtl,
            stringResource(R.string.server_settings_dns_min_ttl),
            icon = Icons.Filled.Timer,
            enabled = editable,
            keyboardType = KeyboardType.Number,
            isError = cache.enabled && !cache.minTtl.isTtl(),
            supporting = stringResource(R.string.server_settings_dns_ttl_hint),
        ) { value -> onEdit { it.copy(minTtl = value) } }
        EditorTextField(
            cache.maxTtl,
            stringResource(R.string.server_settings_dns_max_ttl),
            icon = Icons.Filled.HourglassFull,
            enabled = editable,
            keyboardType = KeyboardType.Number,
            isError = cache.enabled && (!cache.maxTtl.isTtl() || !cache.orderValid),
            supporting =
                stringResource(
                    if (cache.enabled &&
                        !cache.orderValid
                    ) {
                        R.string.server_settings_dns_ttl_order
                    } else {
                        R.string.server_settings_dns_ttl_hint
                    },
                ),
        ) { value -> onEdit { it.copy(maxTtl = value) } }
    }
}
