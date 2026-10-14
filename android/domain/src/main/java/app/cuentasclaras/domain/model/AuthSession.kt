package app.cuentasclaras.domain.model

/**
 * Sesión activa: perfil del usuario + tokens vigentes.
 */
data class AuthSession(
    val user: User,
    val tokens: StoredTokens,
)
