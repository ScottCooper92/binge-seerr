package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.discover.SeerrTitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.sliders.HubCardLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubHeroLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.HubScoreSource
import com.cooper.binge.companion.seerr.discover.sliders.HubTileLoader
import com.cooper.binge.companion.seerr.discover.sliders.SeerrHubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.SeerrHubScoreSource
import com.cooper.binge.companion.seerr.seerr.TitleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** What the discovery hubs read titles through. */
@Module
@InstallIn(SingletonComponent::class)
object HubsModule {
    @Provides
    @Singleton
    fun titleListRepository(
        connection: SeerrConnection,
        @IoDispatcher io: CoroutineDispatcher,
    ): TitleListRepository = SeerrTitleListRepository(api = connection::api, profile = connection::profile, io = io)

    @Provides
    @Singleton
    fun hubMediaSource(
        connection: SeerrConnection,
        titles: TitleCache,
        @IoDispatcher io: CoroutineDispatcher,
    ): HubMediaSource =
        SeerrHubMediaSource(api = connection::api, profile = connection::profile, titles = titles, io = io, serverUrl = {
            connection.current().baseUrl
        })

    @Provides
    fun hubCardLoader(
        titles: TitleListRepository,
        media: HubMediaSource,
    ): HubCardLoader = HubCardLoader(titles, media)

    @Provides
    @Singleton
    fun hubScoreSource(
        connection: SeerrConnection,
        @IoDispatcher io: CoroutineDispatcher,
    ): HubScoreSource = SeerrHubScoreSource(api = connection::api, profile = connection::profile, io = io)

    @Provides
    fun hubHeroLoader(
        titles: TitleListRepository,
        media: HubMediaSource,
        scores: HubScoreSource,
    ): HubHeroLoader = HubHeroLoader(titles, media, scores)

    @Provides
    fun hubTileLoader(titles: TitleListRepository): HubTileLoader = HubTileLoader(titles)
}
