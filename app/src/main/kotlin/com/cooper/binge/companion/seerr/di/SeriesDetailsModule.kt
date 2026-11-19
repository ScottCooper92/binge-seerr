package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.discover.title.SeerrSeriesRepository
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/** What the series page reads, over the connected server. */
@Module
@InstallIn(SingletonComponent::class)
object SeriesDetailsModule {
    @Provides
    fun seriesDetailsRepository(
        connection: SeerrConnection,
        statuses: TitleStatusStore,
        @IoDispatcher io: CoroutineDispatcher,
    ): SeriesDetailsRepository =
        SeerrSeriesRepository(
            api = connection::api,
            viewer = { connection.recentAuthenticatedUser() },
            profile = connection::profile,
            serverUrl = { connection.current().baseUrl },
            deviceRegion = ::deviceRegion,
            io = io,
            statuses = statuses,
        )
}
