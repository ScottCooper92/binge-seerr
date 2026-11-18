package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.discover.collection.CollectionRepository
import com.cooper.binge.companion.seerr.discover.collection.SeerrCollectionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/** What the collection page reads, over the connected server. */
@Module
@InstallIn(SingletonComponent::class)
object CollectionModule {
    @Provides
    fun collectionRepository(
        connection: SeerrConnection,
        @IoDispatcher io: CoroutineDispatcher,
    ): CollectionRepository = SeerrCollectionRepository(api = connection::api, io = io)
}
