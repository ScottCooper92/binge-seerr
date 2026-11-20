package com.cooper.binge.companion.seerr.ui.search

import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.server.AgentOption
import com.cooper.binge.companion.seerr.ui.settings.server.icon
import com.cooper.binge.companion.seerr.ui.settings.server.labelRes
import com.cooper.binge.companion.seerr.ui.settings.server.offeredAgents
import com.cooper.binge.companion.seerr.ui.settings.server.rowKey

/**
 * One entry per option an agent's page draws as its own row, opening that page on it. The agent's name is the entry's path, so the
 * same label on two agents (a webhook address, say) reads apart. Each shows only where the server offers the agent, and the Telegram
 * topic only where the server keeps one, which is when the page draws those rows.
 */
internal val agentOptionEntries: List<IndexEntry> =
    AgentOption.entries
        .filter { it.ownControl }
        .map { option ->
            IndexEntry(
                target = SearchTarget.Agent(option.agent),
                label = option.labelRes(),
                icon = option.icon(),
                section = R.string.settings_section_notifications,
                pageLabel = option.agent.labelRes(),
                row = option.rowKey(),
                shown = { access ->
                    option.agent in access.profile.offeredAgents() &&
                        (option != AgentOption.TelegramMessageThreadId || access.profile.hasTelegramTopics)
                },
            )
        }
