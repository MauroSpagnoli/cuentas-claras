package app.cuentasclaras.domain.model

/**
 * Invitación a un grupo: código corto para compartir junto a su URL pública.
 */
data class Invite(
    val code: String,
    val url: String,
    val expiresAt: String? = null,
)
