package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrFontScalePreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.preview.SeerrSpanishPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrTallComponentPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.LocalEditorIssues
import io.github.scottcooper92.binge.seerr.ui.users.settings.invalid
import io.github.scottcooper92.binge.seerr.ui.users.settings.missing
import kotlinx.coroutines.flow.emptyFlow

/**
 * Settings › Services: the list of Radarr and Sonarr instances, then the instance form and the
 * override rule form it opens. Each takes its layout across the device matrix once and its other
 * arms on the phone cell alone. The instance form is taller than any device cell, so its destination
 * and behaviour sections are framed as components in [DvrInstanceSectionsScreenshotTest].
 */
class ServicesScreenshotTest {
    /** Both kinds with an instance each, flagged 4K and default, and the override rules Jellyseerr 2.2 and later have. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = ServicesFrame(ServicesUiState.Ready(instances = instances(), rules = rules()))

    /** Overseerr and early Jellyseerr have no override rules, so the group is absent rather than empty. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withoutRules() = ServicesFrame(ServicesUiState.Ready(instances = instances(), rules = null))

    /** Nothing configured: each kind offers only its add row, and the rules group offers only its own. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun nothingConfigured() = ServicesFrame(ServicesUiState.Ready(instances = emptyList(), rules = emptyList()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = ServicesFrame(ServicesUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = ServicesFrame(ServicesUiState.Error(SeerrError.Unreachable))
}

class DvrInstanceScreenshotTest {
    /** A new Radarr before any test: the connection fields, and a note that the destination waits on one. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun newRadarrLayout() = DvrFrame(dvrReady(DvrForm.blank(ServiceType.Radarr)))

    /** An existing Radarr whose test answered, so the destination is picked from what it offered. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun testedRadarrLayout() = DvrFrame(dvrReady(radarrForm(), extras = DvrExtras(choices = choices(languages = false))))

    /** A Sonarr adds the series type, season folders and the anime destination. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun testedSonarrLayout() = DvrFrame(dvrReady(sonarrForm(), extras = DvrExtras(choices = choices(languages = true))))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun testing() = DvrFrame(dvrReady(radarrForm(), extras = DvrExtras(testing = true)))

    /** A port that is not a number marks the field and keeps Test and Save off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun invalidPort() = DvrFrame(dvrReady(radarrForm().copy(port = "78x8")))

    /** A bad external URL sits in Advanced, which starts closed: the section opens itself and counts it. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun invalidExternalUrlOpensAdvanced() =
        DvrFrame(dvrReady(radarrForm().copy(externalUrl = "ftp://nope"), extras = DvrExtras(choices = choices(languages = false))))

    /** The same page in Spanish, whose headers and messages run longer. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun testedRadarrSpanish() =
        DvrFrame(dvrReady(radarrForm().copy(port = "78x8"), extras = DvrExtras(choices = choices(languages = false))))

    /** The page at 1.5x and 2x text, where the pinned Cancel and Save bar must still fit. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun testedRadarrLargeText() = DvrFrame(dvrReady(radarrForm(), extras = DvrExtras(choices = choices(languages = false))))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = DvrFrame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = DvrFrame(ExtrasEditorUiState.Error(SeerrError.Server))
}

/** The instance form's lower sections, which a static frame of the whole page never reaches. */
class DvrInstanceSectionsScreenshotTest {
    /** Radarr's destination: minimum availability, profile, folder and tags. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun radarrDestination() =
        SectionFrame {
            DestinationFields(radarrForm(), choices(languages = false), enabled = true, actions = noActions())
        }

    /** Sonarr's destination: a language profile, the series type and season folders, then the anime destination. */
    @PreviewTest
    @SeerrTallComponentPreviews
    @Composable
    fun sonarrDestination() =
        SectionFrame { DestinationFields(sonarrForm(), choices(languages = true), enabled = true, actions = noActions()) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun behaviour() = SectionFrame { FlagSwitches(sonarrForm().copy(syncEnabled = true), enabled = true, actions = noActions()) }

    /** Advanced is closed until asked for, so its own frame is the one place its fields and flags are seen open. */
    @PreviewTest
    @SeerrTallComponentPreviews
    @Composable
    fun advancedOpen() =
        SectionFrame {
            CompositionLocalProvider(
                LocalEditorIssues provides listOf(invalid(DvrSections.ADVANCED, DvrFields.EXTERNAL_URL, R.string.editor_error_web_url)),
            ) {
                AdvancedFields(sonarrForm().copy(externalUrl = "ftp://nope"), enabled = true, actions = noActions())
            }
        }
}

class OverrideRuleScreenshotTest {
    /** A new rule with nothing picked: the instance comes first, and the overrides say they wait on it. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun newRuleLayout() = RuleFrame(ruleReady(OverrideRuleForm(), extras = OverrideRuleExtras(instances = instances(), users = users())))

    /** A saved rule on Radarr, matched on one user and a genre, overriding the profile and folder. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun existingRuleLayout() =
        RuleFrame(
            ruleReady(
                savedRule(),
                extras = OverrideRuleExtras(instances = instances(), users = users(), choices = choices(languages = false)),
            ),
        )

    /** The chosen instance's profiles and folders are being read. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loadingChoices() =
        RuleFrame(
            ruleReady(
                savedRule().copy(profileId = null, rootFolder = null),
                extras = OverrideRuleExtras(instances = instances(), users = users(), loadingChoices = true),
            ),
        )

    /** An instance is picked but not yet tested, so the overrides name that as what is missing. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun instancePickedUntested() =
        RuleFrame(
            ruleReady(
                OverrideRuleForm(serviceType = ServiceType.Sonarr, serviceId = 2),
                extras = OverrideRuleExtras(instances = instances(), users = users()),
            ),
        )

    /** The saved rule in Spanish, whose section headers run longer. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun existingRuleSpanish() =
        RuleFrame(
            ruleReady(
                savedRule(),
                extras = OverrideRuleExtras(instances = instances(), users = users(), choices = choices(languages = false)),
            ),
        )

    /** The saved rule at 1.5x and 2x text, where the pinned Cancel and Save bar must still fit. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun existingRuleLargeText() =
        RuleFrame(
            ruleReady(
                savedRule(),
                extras = OverrideRuleExtras(instances = instances(), users = users(), choices = choices(languages = false)),
            ),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = RuleFrame(ExtrasEditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = RuleFrame(ExtrasEditorUiState.Error(SeerrError.Unauthorized))
}

/** The rule form's sections that a static frame of the whole page never reaches, or never shows flagged. */
class OverrideRuleSectionsScreenshotTest {
    /** What a matching request is sent with: the instance's profile, folder and tags. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun overrides() =
        SectionFrame {
            RuleOverrides(
                OverrideRuleExtras(instances = instances(), users = users(), choices = choices(languages = false)),
                savedRule().copy(tagIds = setOf(1)),
                enabled = true,
                actions = noActions(),
                onToggleTag = {},
            )
        }

    /** A Save without an instance: the section is held open, counts the issue and says it is required. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun missingInstance() =
        SectionFrame {
            CompositionLocalProvider(
                LocalEditorIssues provides listOf(missing(OverrideRuleSections.INSTANCE, OverrideRuleFields.INSTANCE)),
            ) {
                RuleInstance(OverrideRuleExtras(instances = instances()), OverrideRuleForm(), enabled = true, onSelectInstance = {})
            }
        }

    /** A Save on a rule with no condition and no override: both sections are held open and say what they need (#733). */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun missingConditionAndOverride() =
        SectionFrame {
            val draft = OverrideRuleForm(serviceType = ServiceType.Radarr, serviceId = 1)
            val extras = OverrideRuleExtras(instances = instances(), users = users(), choices = choices(languages = false))
            CompositionLocalProvider(LocalEditorIssues provides draft.issues()) {
                RuleConditions(extras, draft, enabled = true, actions = noActions(), onToggleUser = {})
                RuleOverrides(extras, draft, enabled = true, actions = noActions(), onToggleTag = {})
            }
        }
}

private val SECTION_WIDTH = 411.dp
private val SECTION_PADDING = 16.dp
private val SECTION_SPACING = 12.dp

private fun instances() =
    listOf(
        DvrSummary(id = 1, type = ServiceType.Radarr, name = "Radarr", address = "radarr.lan:7878", is4k = false, isDefault = true),
        DvrSummary(id = 2, type = ServiceType.Sonarr, name = "Sonarr 4K", address = "sonarr.lan:8989", is4k = true, isDefault = false),
    )

private fun rules() =
    listOf(
        OverrideRuleSummary(id = 1, instanceName = "Radarr", conditions = 1),
        OverrideRuleSummary(id = 2, instanceName = "Sonarr 4K", conditions = 3),
    )

private fun users() = listOf(Choice(1, "Scott"), Choice(2, "Alex"))

private fun choices(languages: Boolean) =
    DvrChoices(
        profiles = listOf(Choice(1, "Any"), Choice(4, "HD-1080p")),
        rootFolders = listOf("/movies", "/anime"),
        tags = listOf(Choice(1, "kids"), Choice(2, "4k")),
        languageProfiles = if (languages) listOf(Choice(1, "English")) else null,
    )

private fun radarrForm() =
    DvrForm.blank(ServiceType.Radarr).copy(
        id = 1,
        name = "Radarr",
        host = "radarr.lan",
        apiKey = "a1b2c3d4e5",
        isDefault = true,
        profileId = 4,
        profileName = "HD-1080p",
        rootFolder = "/movies",
        tagIds = setOf(1),
    )

private fun sonarrForm() =
    DvrForm.blank(ServiceType.Sonarr).copy(
        id = 2,
        name = "Sonarr 4K",
        host = "sonarr.lan",
        apiKey = "f6g7h8i9j0",
        is4k = true,
        profileId = 4,
        profileName = "HD-1080p",
        rootFolder = "/movies",
        animeProfileId = 1,
        animeRootFolder = "/anime",
        languageProfileId = 1,
        animeLanguageProfileId = 1,
    )

private fun savedRule() =
    OverrideRuleForm(
        id = 1,
        serviceType = ServiceType.Radarr,
        serviceId = 1,
        userIds = setOf(2),
        genres = "16, 28",
        languages = "ja",
        profileId = 4,
        rootFolder = "/anime",
    )

private fun dvrReady(
    form: DvrForm,
    extras: DvrExtras = DvrExtras(),
) = ExtrasEditorUiState.Ready(draft = form, saved = form, extras = extras)

private fun ruleReady(
    form: OverrideRuleForm,
    extras: OverrideRuleExtras,
) = ExtrasEditorUiState.Ready(draft = form, saved = form, extras = extras)

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

@Composable
private fun SectionFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.width(SECTION_WIDTH).padding(SECTION_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        content = content,
    )
}

@Composable
private fun ServicesFrame(state: ServicesUiState) =
    ServicesScreen(
        state = state,
        actions = ServicesActions(onBack = {}, onRetry = {}, onOpenInstance = { _, _ -> }, onOpenRule = {}),
    )

@Composable
private fun DvrFrame(state: ExtrasEditorUiState<DvrForm, DvrExtras>) =
    DvrInstanceScreen(state = state, events = emptyFlow(), actions = noActions(), onTest = {}, onDelete = {})

@Composable
private fun RuleFrame(state: ExtrasEditorUiState<OverrideRuleForm, OverrideRuleExtras>) =
    OverrideRuleScreen(
        state = state,
        events = emptyFlow(),
        actions = noActions(),
        ruleActions = OverrideRuleActions(onSelectInstance = {}, onToggleUser = {}, onToggleTag = {}, onDelete = {}),
    )
