package app.cuentasclaras.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * Garantiza una cabecera `Idempotency-Key` en los métodos de escritura, tal y
 * como exige el contrato. Si la petición ya trae una clave (por ejemplo, un
 * reintento desde el outbox), se respeta.
 */
class IdempotencyInterceptor(
    private val keyFactory: () -> String = { UUID.randomUUID().toString() },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val needsKey = original.method in WRITE_METHODS && original.header(HEADER) == null
        val request = if (needsKey) {
            original.newBuilder().header(HEADER, keyFactory()).build()
        } else {
            original
        }
        return chain.proceed(request)
    }

    companion object {
        const val HEADER = "Idempotency-Key"
        private val WRITE_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
