package com.cooper.binge.companion.seerr.discover.sliders

/**
 * The studios and networks the built-in Studios and Networks sliders show: the fixed lists the providers' web client carries,
 * read from Seerr's `src/components/Discover/StudioSlider` and `NetworkSlider` at tag v3.5.2 (the lists are the same on its
 * main branch). The server serves no such list, so the app holds the TMDB company ids, their names and the logo files. The
 * logos are TMDB's own, drawn through its duotone filter as the web client does, which makes them monochrome artwork.
 */
internal val HUB_STUDIOS: List<HubTile> =
    listOf(
        company(2, "Disney", "/wdrCwmRnLFJhEoH8GSfymY85KHT.png"),
        company(127928, "20th Century Studios", "/h0rjX5vjW5r8yEnUBStFarjcLT4.png"),
        company(34, "Sony Pictures", "/GagSvqWlyPdkFHMfQ3pNq6ix9P.png"),
        company(174, "Warner Bros. Pictures", "/ky0xOc5OrhzkZ1N6KyUxacfQsCk.png"),
        company(33, "Universal", "/8lvHyhjr8oUKOOy2dKXoALWKdp0.png"),
        company(4, "Paramount", "/fycMZt242LVjagMByZOLUGbCvv3.png"),
        company(3, "Pixar", "/1TjvGVDMYsj6JBxOAkUHpPEwLf7.png"),
        company(521, "Dreamworks", "/kP7t6RwGz2AvvTkvnI1uteEwHet.png"),
        company(420, "Marvel Studios", "/hUzeosd33nzE5MCNsZxCGEKTXaQ.png"),
        company(9993, "DC", "/2Tc1P3Ac8M479naPp1kYT3izLS5.png"),
        company(41077, "A24", "/1ZXsGaFPgrgS6ZZGS37AqD5uU12.png"),
    )

internal val HUB_NETWORKS: List<HubTile> =
    listOf(
        company(213, "Netflix", "/wwemzKWzjKYJFfCeiB57q3r4Bcm.png"),
        company(2739, "Disney+", "/gJ8VX6JSu3ciXHuC2dDGAo2lvwM.png"),
        company(1024, "Prime Video", "/ifhbNuuVnlwYy5oXA5VIb2YR8AZ.png"),
        company(2552, "Apple TV+", "/4KAy34EHvRM25Ih8wb82AuGU7zJ.png"),
        company(453, "Hulu", "/pqUTCleNUiTLAVlelGxUgWn1ELh.png"),
        company(49, "HBO", "/tuomPhY2UtuPTqqFnKMVHvSb724.png"),
        company(4353, "Discovery+", "/1D1bS3Dyw4ScYnFWTlBOvJXC3nb.png"),
        company(2, "ABC", "/ndAvF4JLsliGreX87jAc9GdjmJY.png"),
        company(19, "FOX", "/1DSpHrWyOORkL9N2QHX7Adt31mQ.png"),
        company(359, "Cinemax", "/6mSHSquNpfLgDdv6VnOOvC5Uz2h.png"),
        company(174, "AMC", "/pmvRmATOCaDykE6JrVoeYxlFHw3.png"),
        company(67, "Showtime", "/Allse9kbjiP6ExaQrnSpIhkurEi.png"),
        company(318, "Starz", "/8GJjw3HHsAJYwIWKIPBPfqMxlEa.png"),
        company(71, "The CW", "/ge9hzeaU7nMtQ4PjkFlc68dGAJ9.png"),
        company(6, "NBC", "/o3OedEP0f9mfZr33jz2BfXOUK5.png"),
        company(16, "CBS", "/nm8d7P7MJNiBLdgIzUK0gkuEA4r.png"),
        company(4330, "Paramount+", "/fi83B1oztoS47xxcemFdPMhIzK.png"),
        company(4, "BBC One", "/mVn7xESaTNmjBUyUtGNvDQd3CT1.png"),
        company(56, "Cartoon Network", "/c5OC6oVCg6QP4eqzW6XIq17CQjI.png"),
        company(80, "Adult Swim", "/9AKyspxVzywuaMuZ1Bvilu8sXly.png"),
        company(13, "Nickelodeon", "/ikZXxg6GnwpzqiZbRPhJGaZapqB.png"),
        company(3353, "Peacock", "/gIAcGTjKKr0KOHL5s4O36roJ8p7.png"),
    )

private const val TMDB_LOGO_BASE = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)"

private fun company(
    id: Int,
    name: String,
    logoPath: String,
): HubTile = HubTile(id, name, TMDB_LOGO_BASE + logoPath)
