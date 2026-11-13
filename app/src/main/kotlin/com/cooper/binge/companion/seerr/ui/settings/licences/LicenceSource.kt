package com.cooper.binge.companion.seerr.ui.settings.licences

import android.content.Context
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.mikepenz.aboutlibraries.Libs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.mikepenz.aboutlibraries.util.withContext as withAboutLibrariesContext

/**
 * The licences of the libraries the app ships. The build writes the list (the AboutLibraries plugin puts
 * `raw/aboutlibraries.json` in the app's resources) and this reads it. Only the data side of AboutLibraries is used;
 * the page is drawn with the design system's rows.
 */
class LicenceSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        suspend fun load(): List<LicenceEntry> =
            withContext(dispatcher) {
                Libs
                    .Builder()
                    .withAboutLibrariesContext(context)
                    .build()
                    .libraries
                    .map { library ->
                        RawLibrary(
                            id = library.uniqueId,
                            name = library.name,
                            version = library.artifactVersion,
                            licenceNames = library.licenses.map { it.name },
                            website = library.website,
                        )
                    }.toLicenceEntries()
            }
    }
