package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import com.cooper.binge.companion.seerr.seerr.isNoDefaultServerRefusal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

private const val MOVIE_MEDIA_ID = 1
private const val SHOW_MEDIA_ID = 10
private const val SERVER_ID = 1

/** Deleting a title's files through the demo (#1736): it goes through as shipped, and is refused the way Seerr refuses it once the default is gone. */
class DemoDeleteFilesTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    private suspend fun SeerrApi.setDefault(
        service: String,
        isDefault: Boolean,
    ) {
        updateDvr(service, SERVER_ID, SeerrServiceSettingsDto(isDefault = isDefault, syncEnabled = true))
    }

    private suspend fun SeerrApi.refusal(mediaId: Int): HttpException? =
        try {
            deleteMediaFiles(mediaId, is4k = false)
            null
        } catch (failure: HttpException) {
            failure
        }

    @Test
    fun `as it ships, the files of a movie and of a show can be deleted`() =
        demo { api ->
            assertEquals(null, api.refusal(MOVIE_MEDIA_ID))
            assertEquals(null, api.refusal(SHOW_MEDIA_ID))
        }

    @Test
    fun `without a default Radarr a movie's delete is refused as Seerr refuses it, and a show's still goes through`() =
        demo { api ->
            api.setDefault("radarr", isDefault = false)

            val refused = api.refusal(MOVIE_MEDIA_ID)

            assertEquals(409, refused?.code())
            assertTrue("the app reads it as the missing-default refusal", refused?.isNoDefaultServerRefusal() == true)
            assertEquals("a show goes through Sonarr, which still has its default", null, api.refusal(SHOW_MEDIA_ID))
        }

    @Test
    fun `without a default Sonarr a show's delete is refused, and a movie's still goes through`() =
        demo { api ->
            api.setDefault("sonarr", isDefault = false)

            assertTrue(api.refusal(SHOW_MEDIA_ID)?.isNoDefaultServerRefusal() == true)
            assertEquals(null, api.refusal(MOVIE_MEDIA_ID))
        }

    @Test
    fun `making the server the default again lets the delete through`() =
        demo { api ->
            api.setDefault("radarr", isDefault = false)
            assertTrue(api.refusal(MOVIE_MEDIA_ID)?.isNoDefaultServerRefusal() == true)

            api.setDefault("radarr", isDefault = true)

            assertEquals(null, api.refusal(MOVIE_MEDIA_ID))
        }

    @Test
    fun `another server being the default is enough, so un-defaulting the first changes nothing`() =
        demo { api ->
            api.createDvr(
                "radarr",
                SeerrServiceSettingsDto(name = "Second Radarr", hostname = "radarr2.invalid", port = 7878, isDefault = true),
            )
            api.setDefault("radarr", isDefault = false)

            assertEquals(null, api.refusal(MOVIE_MEDIA_ID))
        }
}
