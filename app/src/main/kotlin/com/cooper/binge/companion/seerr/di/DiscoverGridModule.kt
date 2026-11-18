package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.FilterLookups
import com.cooper.binge.companion.seerr.discover.grid.SeerrDiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.SeerrFilterLookups
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** What the "see all" grid reads titles through. */
@Module
@InstallIn(SingletonComponent::class)
object DiscoverGridModule {
    @Provides
    @Singleton
    fun discoverGridRepository(
        connection: SeerrConnection,
        titles: TitleListRepository,
        @IoDispatcher io: CoroutineDispatcher,
    ): DiscoverGridRepository = SeerrDiscoverGridRepository(api = connection::api, profile = connection::profile, titles = titles, io = io)

    @Provides
    @Singleton
    fun filterLookups(
        connection: SeerrConnection,
        @IoDispatcher io: CoroutineDispatcher,
    ): FilterLookups =
        SeerrFilterLookups(
            api = connection::api,
            profile = connection::profile,
            viewer = { connection.recentAuthenticatedUser() },
            io = io,
        )
}
