package app.cuentasclaras.domain.model

/**
 * Grupo de gastos compartidos. `version` es el contador de concurrencia optimista
 * que devuelve el backend (Fase 8 lo usará para resolver conflictos).
 */
data class Group(
    val id: String,
    val name: String,
    val description: String? = null,
    val currency: String = "EUR",
    val inviteCode: String? = null,
    val memberCount: Int = 0,
    val createdAt: String? = null,
    val version: Long = 0,
)

/** Rol de un miembro dentro del grupo. */
enum class GroupRole {
    OWNER,
    ADMIN,
    MEMBER;

    companion object {
        /** Convierte el valor del backend; ante un valor desconocido degrada a [MEMBER]. */
        fun fromApi(raw: String?): GroupRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEMBER
    }
}

/** Estado de la relación entre un usuario y un grupo. */
enum class MemberStatus {
    INVITED,
    ACTIVE,
    LEFT;

    companion object {
        /** Convierte el valor del backend; ante un valor desconocido asume [ACTIVE]. */
        fun fromApi(raw: String?): MemberStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}
