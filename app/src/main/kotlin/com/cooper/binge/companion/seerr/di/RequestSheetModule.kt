package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.SeerrRequestSheetRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/** What the in-app request sheet reads and writes, over the connected server. */
@Module
@InstallIn(SingletonComponent::class)
object RequestSheetModule {
    @Provides
    fun requestSheetRepository(
        connection: SeerrConnection,
        statuses: TitleStatusStore,
        @IoDispatcher io: CoroutineDispatcher,
    ): RequestSheetRepository =
        SeerrRequestSheetRepository(
            api = connection::api,
            viewer = connection::authenticatedUser,
            profile = connection::profile,
            io = io,
            statuses = statuses,
        )
}
