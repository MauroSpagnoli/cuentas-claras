package app.cuentasclaras.core.network

/**
 * Configuración de red aportada por la capa de aplicación (que sí conoce
 * `BuildConfig`). `baseUrl` debe apuntar a la raíz de la API, p. ej.
 * `http://10.0.2.2:8080/api/v1/`.
 */
interface NetworkConfig {
    val baseUrl: String
    val enableLogging: Boolean
}

/** Garantiza la barra final que Retrofit exige en `baseUrl`. */
val NetworkConfig.normalizedBaseUrl: String
    get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
