package com.cooper.binge.companion.seerr.ui.grid

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.feature.discover.R

/** What a sort is called, in the user's language. A movie's date and a show's, and a movie's title and a show's, read the same. */
@StringRes
fun DiscoverSort.labelRes(): Int =
    when (this) {
        DiscoverSort.PopularityDesc -> R.string.grid_sort_popularity_desc
        DiscoverSort.PopularityAsc -> R.string.grid_sort_popularity_asc
        DiscoverSort.RatingDesc -> R.string.grid_sort_rating_desc
        DiscoverSort.RatingAsc -> R.string.grid_sort_rating_asc
        DiscoverSort.VotesDesc -> R.string.grid_sort_votes_desc
        DiscoverSort.VotesAsc -> R.string.grid_sort_votes_asc
        DiscoverSort.ReleaseNewest, DiscoverSort.FirstAirNewest -> R.string.grid_sort_date_desc
        DiscoverSort.ReleaseOldest, DiscoverSort.FirstAirOldest -> R.string.grid_sort_date_asc
        DiscoverSort.RevenueDesc -> R.string.grid_sort_revenue_desc
        DiscoverSort.RevenueAsc -> R.string.grid_sort_revenue_asc
        DiscoverSort.TitleAsc, DiscoverSort.NameAsc -> R.string.grid_sort_title_asc
        DiscoverSort.TitleDesc, DiscoverSort.NameDesc -> R.string.grid_sort_title_desc
    }

fun DiscoverSort.icon(): ImageVector =
    when (this) {
        DiscoverSort.PopularityDesc, DiscoverSort.PopularityAsc -> Icons.Filled.TrendingUp
        DiscoverSort.RatingDesc, DiscoverSort.RatingAsc -> Icons.Filled.Star
        DiscoverSort.VotesDesc, DiscoverSort.VotesAsc -> Icons.Filled.HowToVote
        DiscoverSort.ReleaseNewest, DiscoverSort.FirstAirNewest -> Icons.Filled.NewReleases
        DiscoverSort.ReleaseOldest, DiscoverSort.FirstAirOldest -> Icons.Filled.History
        DiscoverSort.RevenueDesc, DiscoverSort.RevenueAsc -> Icons.Filled.AttachMoney
        DiscoverSort.TitleAsc, DiscoverSort.TitleDesc, DiscoverSort.NameAsc, DiscoverSort.NameDesc -> Icons.Filled.SortByAlpha
    }
