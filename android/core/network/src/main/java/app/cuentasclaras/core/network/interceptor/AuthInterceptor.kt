package app.cuentasclaras.core.network.interceptor

import app.cuentasclaras.domain.auth.AuthTokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Añade `Authorization: Bearer <accessToken>` a cada petición cuando hay tokens,
 * salvo que la petición ya traiga la cabecera (p. ej. tras un refresh).
 */
class AuthInterceptor(
    private val tokenStore: AuthTokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val accessToken = tokenStore.snapshot()?.accessToken
        val shouldAttach = !accessToken.isNullOrBlank() && original.header(HEADER) == null
        if (!shouldAttach) {
            return chain.proceed(original)
        }
        val authenticated = original.newBuilder()
            .header(HEADER, "Bearer $accessToken")
            .build()
        return chain.proceed(authenticated)
    }

    private companion object {
        const val HEADER = "Authorization"
    }
}
