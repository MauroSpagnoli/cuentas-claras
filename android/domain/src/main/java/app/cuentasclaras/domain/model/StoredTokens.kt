package app.cuentasclaras.domain.model

/**
 * Tokens de sesión tal y como se persisten localmente.
 * `expiresAtEpochSeconds` es el instante absoluto de expiración del access token.
 */
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
)
