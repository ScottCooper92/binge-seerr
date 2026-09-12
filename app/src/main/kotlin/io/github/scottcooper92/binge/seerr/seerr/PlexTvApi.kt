package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * The two plex.tv calls behind a Plex sign-in: mint a PIN, then poll it until the user has
 * approved it in their browser and it carries a token. Seerr's own `auth/plex` takes that token.
 * Not a Seerr endpoint: it is plex.tv's public API, spoken to directly, as Seerr's web client does.
 */
interface PlexTvApi {
    @POST("api/v2/pins?strong=true")
    suspend fun createPin(): PlexPinDto

    @GET("api/v2/pins/{id}")
    suspend fun pin(
        @Path("id") id: Long,
    ): PlexPinDto
}

/** [authToken] is null until the user approves the PIN; [expiresAt] is plex.tv's ISO deadline for that. */
@Serializable
data class PlexPinDto(
    @SerialName("id") val id: Long,
    @SerialName("code") val code: String,
    @SerialName("authToken") val authToken: String? = null,
    @SerialName("expiresAt") val expiresAt: String? = null,
)

/**
 * How this install introduces itself to plex.tv. [identifier] must be stable per install: it is
 * what plex.tv lists under the user's authorised devices, and a changing one is a new device each
 * sign-in. [product] is the name they see there.
 */
data class PlexClientIdentity(
    val identifier: String,
    val product: String,
    val version: String,
    val device: String,
    val platform: String = "Android",
) {
    /** The page the user approves the PIN on. plex.tv reads the parameters from the fragment. */
    fun authUrl(code: String): String =
        "https://app.plex.tv/auth#?clientID=${identifier.urlEncoded()}&code=${code.urlEncoded()}" +
            "&context%5Bdevice%5D%5Bproduct%5D=${product.urlEncoded()}"

    private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}

private const val PLEX_TV_BASE_URL = "https://plex.tv/"
private const val TIMEOUT_SECONDS = 15L

/** A [PlexTvApi] presenting [identity] on every call; [baseUrl] is a parameter so a test can script plex.tv. */
fun plexTvApi(
    identity: PlexClientIdentity,
    baseUrl: String = PLEX_TV_BASE_URL,
): PlexTvApi {
    val client =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("Accept", "application/json")
                        .header("X-Plex-Product", identity.product)
                        .header("X-Plex-Version", identity.version)
                        .header("X-Plex-Client-Identifier", identity.identifier)
                        .header("X-Plex-Platform", identity.platform)
                        .header("X-Plex-Device", identity.device)
                        .build(),
                )
            }.connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    val json = Json { ignoreUnknownKeys = true }
    return Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()
        .create(PlexTvApi::class.java)
}
