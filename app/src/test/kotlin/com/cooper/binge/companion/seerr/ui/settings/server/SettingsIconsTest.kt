package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsBackupRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The icons the server settings pick by value rather than by a fixed enum entry. */
class SettingsIconsTest {
    @Test
    fun `each ntfy priority level has its own icon`() {
        val icons = NtfyPriorityLevel.entries.map { ntfyPriorityIcon(it.value) }
        assertEquals(NtfyPriorityLevel.entries.map { it.icon() }, icons)
        assertEquals(icons.size, icons.toSet().size)
    }

    @Test
    fun `a blank priority reads as left to the server, and an unknown number takes the setting's icon`() {
        assertEquals(Icons.Filled.SettingsBackupRestore, ntfyPriorityIcon(""))
        assertNull(ntfyPriorityIcon("9"))
    }

    @Test
    fun `a custom slider kind that comes in movies and TV shows which one`() {
        listOf(
            SliderType.MovieGenre to SliderType.TvGenre,
            SliderType.MovieStreamingServices to SliderType.TvStreamingServices,
            SliderType.PopularMovies to SliderType.PopularTv,
            SliderType.UpcomingMovies to SliderType.UpcomingTv,
        ).forEach { (movie, tv) -> assertNotEquals(movie.name, movie.icon(), tv.icon()) }
    }

    @Test
    fun `a library's switch tells movies from shows`() {
        assertNotEquals(LibraryType.Movies.icon(), LibraryType.Shows.icon())
    }
}
