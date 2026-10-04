package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssue
import io.github.scottcooper92.binge.seerr.ui.users.settings.missing

/** The section ids of the override rule form, shared by its validator and the composables that tag themselves with them. */
internal object OverrideRuleSections {
    const val INSTANCE = "instance"
    const val CONDITIONS = "conditions"
    const val OVERRIDES = "overrides"
}

/** The field ids an [OverrideRuleForm] issue can name. */
internal object OverrideRuleFields {
    const val INSTANCE = "instance"
    const val CONDITIONS = "conditions"
    const val OVERRIDES = "overrides"
}

internal const val OVERRIDE_RULE_FORM_KEY = "override_rule"

/**
 * Everything standing between this draft and a save, in the order the sections read. A rule needs
 * the instance it applies to, at least one condition and at least one override. That is the rule
 * Seerr's web client enforces in `OverrideRuleModal.tsx`; the server itself stores whatever it is
 * sent. A rule with no condition would apply to every request, and one with no override does
 * nothing. The list is empty exactly when [OverrideRuleForm.valid] is true, which
 * `OverrideRuleValidationTest` holds it to.
 */
internal fun OverrideRuleForm.issues(): List<EditorIssue> =
    listOfNotNull(
        missing(OverrideRuleSections.INSTANCE, OverrideRuleFields.INSTANCE).takeIf { serviceId == null || serviceType == null },
        missing(
            OverrideRuleSections.CONDITIONS,
            OverrideRuleFields.CONDITIONS,
            R.string.server_settings_rule_needs_condition,
        ).takeUnless { hasCondition },
        missing(
            OverrideRuleSections.OVERRIDES,
            OverrideRuleFields.OVERRIDES,
            R.string.server_settings_rule_needs_override,
        ).takeUnless { hasOverride },
    )
