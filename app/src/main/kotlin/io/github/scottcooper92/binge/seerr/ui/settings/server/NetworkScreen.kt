package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionTitle
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSwitchRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
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
        EditorSwitchRow(stringResource(R.string.server_settings_trust_proxy), draft.trustProxy, enabled = enabled) { value ->
            actions.onEdit { it.copy(trustProxy = value) }
        }
        EditorSwitchRow(stringResource(R.string.server_settings_csrf), draft.csrfProtection, enabled = enabled) { value ->
            actions.onEdit { it.copy(csrfProtection = value) }
        }
        draft.forceIpv4First?.let { on ->
            EditorSwitchRow(stringResource(R.string.server_settings_force_ipv4), on, enabled = enabled) { value ->
                actions.onEdit { it.copy(forceIpv4First = value) }
            }
        }
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
    EditorSectionTitle(stringResource(R.string.server_settings_proxy))
    EditorSwitchRow(stringResource(R.string.server_settings_proxy_enabled), proxy.enabled, enabled = enabled) { value ->
        onEdit { it.copy(enabled = value) }
    }
    // The switch is the gate: with the proxy off nothing below it is read, and ProxyForm.valid
    // already says so, so the controls follow rather than looking live over a setting in no use.
    val editable = enabled && proxy.enabled
    EditorTextField(
        proxy.host,
        stringResource(R.string.server_settings_host),
        enabled = editable,
        keyboardType = KeyboardType.Uri,
        placeholder = stringResource(R.string.placeholder_proxy_host),
        isError = proxy.enabled && proxy.host.isBlank(),
    ) { value -> onEdit { it.copy(host = value) } }
    EditorTextField(
        proxy.port,
        stringResource(R.string.server_settings_port),
        enabled = editable,
        keyboardType = KeyboardType.Number,
        placeholder = stringResource(R.string.placeholder_port_proxy),
        isError = proxy.enabled && !proxy.valid && proxy.host.isNotBlank(),
    ) { value -> onEdit { it.copy(port = value) } }
    EditorSwitchRow(stringResource(R.string.server_settings_use_ssl), proxy.useSsl, enabled = editable) { value ->
        onEdit {
            it.copy(useSsl = value)
        }
    }
    EditorTextField(proxy.user, stringResource(R.string.server_settings_agent_username), enabled = editable) { value ->
        onEdit {
            it.copy(user = value)
        }
    }
    EditorTextField(proxy.password, stringResource(R.string.server_settings_agent_password), enabled = editable, secret = true) { value ->
        onEdit { it.copy(password = value) }
    }
    EditorTextField(
        proxy.bypassFilter,
        stringResource(R.string.server_settings_proxy_bypass),
        enabled = editable,
        supporting = stringResource(R.string.server_settings_proxy_bypass_hint),
    ) { value -> onEdit { it.copy(bypassFilter = value) } }
    EditorSwitchRow(stringResource(R.string.server_settings_proxy_bypass_local), proxy.bypassLocalAddresses, enabled = editable) { value ->
        onEdit { it.copy(bypassLocalAddresses = value) }
    }
}

@Composable
private fun DnsCacheFields(
    cache: DnsCacheForm,
    enabled: Boolean,
    onEdit: ((DnsCacheForm) -> DnsCacheForm) -> Unit,
) {
    EditorSectionTitle(stringResource(R.string.server_settings_dns_cache))
    EditorSwitchRow(stringResource(R.string.server_settings_dns_cache_enabled), cache.enabled, enabled = enabled) { value ->
        onEdit { it.copy(enabled = value) }
    }
    val editable = enabled && cache.enabled
    EditorTextField(
        cache.minTtl,
        stringResource(R.string.server_settings_dns_min_ttl),
        enabled = editable,
        keyboardType = KeyboardType.Number,
        isError = cache.enabled && !cache.minTtl.isTtl(),
        supporting = stringResource(R.string.server_settings_dns_ttl_hint),
    ) { value -> onEdit { it.copy(minTtl = value) } }
    EditorTextField(
        cache.maxTtl,
        stringResource(R.string.server_settings_dns_max_ttl),
        enabled = editable,
        keyboardType = KeyboardType.Number,
        isError = cache.enabled && !cache.maxTtl.isTtl(),
        supporting = stringResource(R.string.server_settings_dns_ttl_hint),
    ) { value -> onEdit { it.copy(maxTtl = value) } }
}
