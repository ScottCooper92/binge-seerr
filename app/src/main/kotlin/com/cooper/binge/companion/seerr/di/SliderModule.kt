package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.discover.sliders.DiscoverSliderRepository
import com.cooper.binge.companion.seerr.discover.sliders.SeerrDiscoverSliderRepository
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** The one copy of the server's discover slider list, shared by the editor and the hubs. */
@Module
@InstallIn(SingletonComponent::class)
object SliderModule {
    @Provides
    @Singleton
    fun discoverSliderRepository(
        connection: SeerrConnection,
        @IoDispatcher io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DiscoverSliderRepository =
        SeerrDiscoverSliderRepository(
            api = connection::api,
            profile = connection::profile,
            serverChanges = connection.credentials,
            scope = scope,
            io = io,
        )
}
