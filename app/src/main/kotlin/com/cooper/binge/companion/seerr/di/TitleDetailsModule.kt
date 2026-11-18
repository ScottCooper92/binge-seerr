package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.discover.title.SeerrTitleDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleDetailsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/** What the title pages read, over the connected server. */
@Module
@InstallIn(SingletonComponent::class)
object TitleDetailsModule {
    @Provides
    fun titleDetailsRepository(
        connection: SeerrConnection,
        @IoDispatcher io: CoroutineDispatcher,
    ): TitleDetailsRepository =
        SeerrTitleDetailsRepository(
            api = connection::api,
            viewer = { connection.recentAuthenticatedUser() },
            profile = connection::profile,
            serverUrl = { connection.current().baseUrl },
            io = io,
        )
}
