package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.discover.person.PersonRepository
import com.cooper.binge.companion.seerr.discover.person.SeerrPersonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/** What the person page reads, over the connected server. */
@Module
@InstallIn(SingletonComponent::class)
object PersonModule {
    @Provides
    fun personRepository(
        connection: SeerrConnection,
        statuses: TitleStatusStore,
        @IoDispatcher io: CoroutineDispatcher,
    ): PersonRepository =
        SeerrPersonRepository(api = connection::api, serverUrl = { connection.current().baseUrl }, io = io, statuses = statuses)
}
