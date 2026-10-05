package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssue
import io.github.scottcooper92.binge.seerr.ui.users.settings.invalid
import io.github.scottcooper92.binge.seerr.ui.users.settings.missing

/** The section ids of an agent's page, shared by its validator and the composables that tag themselves with them. */
internal object AgentSections {
    const val SETTINGS = "settings"
    const val MORE_SETTINGS = "more_settings"
    const val TYPES = "types"
}

/** The section an option sits in: what the agent cannot send without, or the rest. */
internal val AgentOption.sectionId: String get() = if (required) AgentSections.SETTINGS else AgentSections.MORE_SETTINGS

/** Each option's field id, so an issue on it is shown beside it. */
internal val AgentOption.fieldId: String get() = name

internal const val AGENT_FORM_KEY = "notification_agent"

/**
 * Everything standing between this draft and a save. An agent that is off is not read, so it has
 * none. One that is on needs each [AgentOption.required] option: a blank one is missing, and a number
 * that does not parse, or a port outside 1-65535, is invalid. The one required number is the SMTP
 * port, so that message is the port's. It is empty exactly when [AgentForm.valid] is true, which
 * `NotificationAgentValidationTest` holds it to.
 */
internal fun AgentForm.issues(): List<EditorIssue> {
    if (!enabled) return emptyList()
    return AgentOption.of(agent).filter { it.required && !it.satisfiedBy(options[it].orEmpty()) }.map { option ->
        if (options[option].isNullOrBlank()) {
            missing(option.sectionId, option.fieldId)
        } else {
            invalid(option.sectionId, option.fieldId, R.string.editor_error_port)
        }
    }
}
