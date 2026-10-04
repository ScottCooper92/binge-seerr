package io.github.scottcooper92.binge.seerr.ui.settings.server

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
}

internal const val OVERRIDE_RULE_FORM_KEY = "override_rule"

/**
 * Everything standing between this draft and a save. A rule needs only the instance it applies to:
 * the conditions and the overrides are each optional. It is empty exactly when [OverrideRuleForm.valid]
 * is true, which `OverrideRuleValidationTest` holds it to.
 */
internal fun OverrideRuleForm.issues(): List<EditorIssue> =
    listOfNotNull(
        missing(OverrideRuleSections.INSTANCE, OverrideRuleFields.INSTANCE).takeIf { serviceId == null || serviceType == null },
    )
