package app.cuentasclaras.domain.model

/**
 * Perfil del usuario autenticado. `createdAt` se mantiene como cadena ISO-8601
 * para no forzar desugaring de `java.time` en `minSdk 24`.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val defaultCurrency: String,
    val locale: String? = null,
    val createdAt: String? = null,
)
