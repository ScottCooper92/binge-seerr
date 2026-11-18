package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.toInitials
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.discover.person.PersonGender
import com.cooper.binge.companion.seerr.ui.discover.title.toDisplayDate
import java.text.NumberFormat
import java.util.Locale
import com.binge.designsystem.R as DesR

private const val NAME_LINES = 2
private const val PORTRAIT_ASPECT = 2f / 3f

/** The portrait beside the eyebrow, name and stats, under a bar that floats over it. */
@Composable
internal fun PersonProfile(
    person: PersonDetails,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.padding_m)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        PersonPortrait(person)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            Column {
                person.knownFor?.let {
                    Text(
                        it.uppercase(LocalConfiguration.current.locales[0]),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    person.name,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = NAME_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            person.stats(LocalConfiguration.current.locales[0]).forEach { (label, value) -> PersonStat(label, value) }
        }
    }
}

/** The design system has no portrait of this shape yet, so this is its [BingeShapes.ProfilePoster] with initials where there is no photo. */
@Composable
private fun PersonPortrait(person: PersonDetails) {
    val initials = person.name.toInitials(locale = LocalConfiguration.current.locales[0])
    Box(
        modifier =
            Modifier
                .width(dimensionResource(R.dimen.person_portrait_width))
                .aspectRatio(PORTRAIT_ASPECT)
                .clip(BingeShapes.ProfilePoster)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder = @Composable {
            Text(initials, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (person.photoUrl.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = person.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { placeholder() } },
                error = { Box(Modifier.fillMaxSize(), Alignment.Center) { placeholder() } },
            )
        }
    }
}

@Composable
private fun PersonStat(
    label: String,
    value: String,
) {
    Column {
        Text(
            label.uppercase(LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_xxs)),
        )
    }
}

/** The label and value of each fact the server gave, in reading order. A fact it did not give is left out. */
@Composable
private fun PersonDetails.stats(locale: Locale): List<Pair<String, String>> {
    val born = birthday.toDisplayDate(locale)
    val died =
        deathday.toDisplayDate(locale)?.let { date -> ageAtDeath?.let { stringResource(R.string.person_died_aged, date, it) } ?: date }
    val gender =
        when (gender) {
            PersonGender.Female -> stringResource(R.string.person_gender_female)
            PersonGender.Male -> stringResource(R.string.person_gender_male)
            PersonGender.NonBinary -> stringResource(R.string.person_gender_non_binary)
            null -> null
        }
    val popularity = popularity?.let { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it) }
    return listOfNotNull(
        alsoKnownAs.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.person_page_also_known_as) to it.joinToString(", ") },
        born?.let { stringResource(R.string.person_page_born) to it },
        died?.let { stringResource(R.string.person_page_died) to it },
        placeOfBirth?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.person_stat_from) to it },
        gender?.let { stringResource(R.string.person_stat_gender) to it },
        popularity?.let { stringResource(R.string.person_stat_popularity) to it },
    )
}
