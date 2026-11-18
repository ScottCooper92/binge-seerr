package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.ui.discover.title.toDisplayDate
import java.util.Locale
import com.binge.designsystem.R as DesR

private const val NAME_LINES = 3

/** A person's portrait with their name and what they are known for, then the dates, place and other names, then the biography. */
@Composable
internal fun PersonProfile(
    person: PersonDetails,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            BingeInitialsAvatar(name = person.name, avatarUrl = person.photoUrl, size = dimensionResource(R.dimen.person_portrait_size))
            Column(Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.headlineSmall, maxLines = NAME_LINES, overflow = TextOverflow.Ellipsis)
                person.knownFor?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val rows = person.factRows(LocalConfiguration.current.locales[0])
        if (rows.isNotEmpty()) ItemGroup(title = null, rows = rows)
        person.biography?.let { ExpandableOverview(text = it) }
    }
}

/** The dates, place and other names, each with an icon and none of them opening anything. A fact the server did not give is left out. */
@Composable
private fun PersonDetails.factRows(locale: Locale): List<ListItem> {
    @Composable
    fun row(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: Int,
        value: String?,
    ) = value
        ?.takeIf {
            it.isNotBlank()
        }?.let { ListItem(icon = icon, label = stringResource(label), detail = it, supportingMaxLines = 3, clickable = false) }
    return listOfNotNull(
        row(Icons.Filled.CalendarMonth, R.string.person_page_born, birthday.toDisplayDate(locale)),
        row(Icons.Filled.Event, R.string.person_page_died, deathday.toDisplayDate(locale)),
        row(Icons.Filled.Place, R.string.person_page_place, placeOfBirth),
        row(Icons.Filled.Person, R.string.person_page_also_known_as, alsoKnownAs.joinToString(", ")),
    )
}
