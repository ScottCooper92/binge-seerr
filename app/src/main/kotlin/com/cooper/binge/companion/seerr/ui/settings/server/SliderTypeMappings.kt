package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.seerr.R

/**
 * A kind's icon, in the slider list's rows and the kind picker alike: what it shows, and movies or TV where a kind comes
 * in both. The two keyword kinds differ by glyph alone, since a key and a tag say nothing of movies or TV; the kind's
 * label says which.
 */
internal fun SliderType.icon(): ImageVector =
    when (this) {
        SliderType.RecentlyAdded -> Icons.Filled.LibraryAdd
        SliderType.RecentRequests -> Icons.Filled.Inbox
        SliderType.Watchlist -> Icons.Filled.Bookmark
        SliderType.Trending -> Icons.AutoMirrored.Filled.TrendingUp
        SliderType.PopularMovies -> Icons.Filled.Movie
        SliderType.PopularTv -> Icons.Filled.Tv
        SliderType.UpcomingMovies -> Icons.Filled.Theaters
        SliderType.UpcomingTv -> Icons.Filled.Event
        SliderType.MovieGenres, SliderType.MovieGenre -> Icons.Filled.TheaterComedy
        SliderType.TvGenres, SliderType.TvGenre -> Icons.Filled.Category
        SliderType.Studios, SliderType.Studio -> Icons.Filled.Business
        SliderType.Networks, SliderType.Network -> Icons.Filled.SettingsInputAntenna
        SliderType.MovieKeyword -> Icons.Filled.Key
        SliderType.TvKeyword -> Icons.Filled.Tag
        SliderType.Search -> Icons.Filled.Search
        SliderType.MovieStreamingServices -> Icons.Filled.Subscriptions
        SliderType.TvStreamingServices -> Icons.Filled.LiveTv
    }

@StringRes
internal fun SliderType.labelRes(): Int =
    when (this) {
        SliderType.RecentlyAdded -> R.string.server_settings_slider_recently_added
        SliderType.RecentRequests -> R.string.server_settings_slider_recent_requests
        SliderType.Watchlist -> R.string.server_settings_slider_watchlist
        SliderType.Trending -> R.string.server_settings_slider_trending
        SliderType.PopularMovies -> R.string.server_settings_slider_popular_movies
        SliderType.MovieGenres -> R.string.server_settings_slider_movie_genres
        SliderType.UpcomingMovies -> R.string.server_settings_slider_upcoming_movies
        SliderType.Studios -> R.string.server_settings_slider_studios
        SliderType.PopularTv -> R.string.server_settings_slider_popular_tv
        SliderType.TvGenres -> R.string.server_settings_slider_tv_genres
        SliderType.UpcomingTv -> R.string.server_settings_slider_upcoming_tv
        SliderType.Networks -> R.string.server_settings_slider_networks
        SliderType.MovieKeyword -> R.string.server_settings_slider_movie_keyword
        SliderType.TvKeyword -> R.string.server_settings_slider_tv_keyword
        SliderType.MovieGenre -> R.string.server_settings_slider_movie_genre
        SliderType.TvGenre -> R.string.server_settings_slider_tv_genre
        SliderType.Studio -> R.string.server_settings_slider_studio
        SliderType.Network -> R.string.server_settings_slider_network
        SliderType.Search -> R.string.server_settings_slider_search
        SliderType.MovieStreamingServices -> R.string.server_settings_slider_movie_streaming
        SliderType.TvStreamingServices -> R.string.server_settings_slider_tv_streaming
    }

/** What a custom kind's data holds, in the shape the web client stores it. */
@StringRes
internal fun SliderType.dataHintRes(): Int =
    when (this) {
        SliderType.MovieGenre, SliderType.TvGenre -> R.string.server_settings_slider_data_genre
        SliderType.Studio, SliderType.Network -> R.string.server_settings_slider_data_company
        SliderType.Search -> R.string.server_settings_slider_data_search
        SliderType.MovieStreamingServices, SliderType.TvStreamingServices -> R.string.server_settings_slider_data_streaming
        else -> R.string.server_settings_slider_data_generic
    }
