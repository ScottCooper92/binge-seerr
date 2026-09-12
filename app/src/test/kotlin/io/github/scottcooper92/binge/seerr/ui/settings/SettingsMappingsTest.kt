package io.github.scottcooper92.binge.seerr.ui.settings

import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultQuotaDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultQuotasDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServiceSettingsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val REQUEST = 32
private const val AUTO_APPROVE = 128
private const val ADMIN = 2

class SettingsMappingsTest {
    @Test
    fun `default access reads the request and auto-approve bits, and admin implies both`() {
        assertEquals(SeerrDefaultAccess.NoRequests, SeerrDefaultAccess.fromBits(0))
        assertEquals(SeerrDefaultAccess.NoRequests, SeerrDefaultAccess.fromBits(null))
        assertEquals(SeerrDefaultAccess.RequestWithApproval, SeerrDefaultAccess.fromBits(REQUEST))
        assertEquals(SeerrDefaultAccess.AutoApprove, SeerrDefaultAccess.fromBits(REQUEST or AUTO_APPROVE))
        assertEquals(SeerrDefaultAccess.AutoApprove, SeerrDefaultAccess.fromBits(ADMIN))
    }

    @Test
    fun `a global limit needs a count, and a missing window is a day`() {
        val policy =
            SeerrMainSettingsDto(
                defaultPermissions = REQUEST,
                defaultQuotas =
                    SeerrDefaultQuotasDto(
                        movie = SeerrDefaultQuotaDto(quotaLimit = 5, quotaDays = 7),
                        tv = SeerrDefaultQuotaDto(quotaLimit = 3),
                    ),
            ).toRequestPolicy()

        assertEquals(RequestLimit(count = 5, days = 7), policy.movieLimit)
        assertEquals(RequestLimit(count = 3, days = 1), policy.tvLimit)
        assertNull(
            SeerrMainSettingsDto(
                defaultQuotas = SeerrDefaultQuotasDto(movie = SeerrDefaultQuotaDto(quotaLimit = 0)),
            ).toRequestPolicy().movieLimit,
        )
    }

    @Test
    fun `a service opens at its external URL, else at an address built from its host`() {
        val external =
            SeerrServiceSettingsDto(
                name = "Radarr 4K",
                externalUrl = "https://radarr.example.com/",
                is4k = true,
            ).toService(ServiceType.Radarr)
        val built =
            SeerrServiceSettingsDto(
                hostname = "10.0.0.5",
                port = 8989,
                useSsl = false,
                baseUrl = "/sonarr/",
                isDefault = true,
            ).toService(ServiceType.Sonarr)
        val nowhere = SeerrServiceSettingsDto().toService(ServiceType.Sonarr)

        assertEquals("https://radarr.example.com", external.url)
        assertEquals("Radarr 4K", external.name)
        assertEquals("http://10.0.0.5:8989/sonarr", built.url)
        assertEquals("Sonarr", built.name)
        assertNull(nowhere.url)
    }
}
