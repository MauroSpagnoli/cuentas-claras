package app.cuentasclaras.domain.model

/**
 * Miembro de un grupo tal como lo expone la API (usuario + su rol/estado).
 */
data class GroupMember(
    val userId: String,
    val displayName: String,
    val email: String,
    val role: GroupRole,
    val status: MemberStatus,
    val joinedAt: String? = null,
)
