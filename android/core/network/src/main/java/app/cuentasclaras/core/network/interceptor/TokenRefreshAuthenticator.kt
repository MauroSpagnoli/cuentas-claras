package app.cuentasclaras.core.network.interceptor

import app.cuentasclaras.core.network.dto.AuthTokensDto
import app.cuentasclaras.core.network.dto.RefreshRequestDto
import app.cuentasclaras.domain.auth.AuthTokenStore
import app.cuentasclaras.domain.model.StoredTokens
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/**
 * Renueva el access token de forma transparente ante un `401`.
 *
 * Se apoya en un [OkHttpClient] sin este mismo authenticator para evitar bucles
 * y usa un lock para que peticiones concurrentes no disparen varios refresh.
 * Si el refresh falla, limpia el almacenamiento (sesión cerrada).
 */
class TokenRefreshAuthenticator(
    private val tokenStore: AuthTokenStore,
    private val json: Json,
    private val baseUrl: String,
    private val refreshClient: OkHttpClient,
    private val onSessionExpired: () -> Unit = {},
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val original = response.request
        val header = original.header(AUTHORIZATION) ?: return null
        if (responseCount(response) >= MAX_ATTEMPTS) {
            clearSession()
            return null
        }

        val staleAccessToken = header.removePrefix(BEARER_PREFIX).trim()

        synchronized(lock) {
            val current = tokenStore.snapshot()
            // Otra petición ya renovó el token mientras esperábamos el lock.
            if (current != null && current.accessToken != staleAccessToken) {
                return original.withBearer(current.accessToken)
            }
            val refreshToken = current?.refreshToken
            if (refreshToken.isNullOrBlank()) {
                clearSession()
                return null
            }
            val refreshed = performRefresh(refreshToken)
            if (refreshed == null) {
                clearSession()
                return null
            }
            tokenStore.save(refreshed)
            return original.withBearer(refreshed.accessToken)
        }
    }

    private fun performRefresh(refreshToken: String): StoredTokens? {
        val payload = json.encodeToString(RefreshRequestDto.serializer(), RefreshRequestDto(refreshToken))
        val request = Request.Builder()
            .url(baseUrl + REFRESH_PATH)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.string() ?: return null
                val tokens = json.decodeFromString(AuthTokensDto.serializer(), raw)
                StoredTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresAtEpochSeconds = nowEpochSeconds() + tokens.expiresIn,
                )
            }
        } catch (io: IOException) {
            null
        }
    }

    private fun clearSession() {
        tokenStore.clear()
        onSessionExpired()
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header(AUTHORIZATION, "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val REFRESH_PATH = "auth/refresh"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
