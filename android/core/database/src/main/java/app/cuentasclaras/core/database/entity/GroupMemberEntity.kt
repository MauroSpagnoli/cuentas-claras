package app.cuentasclaras.core.database.entity

import androidx.room.Entity

/**
 * Miembro de un grupo. La clave primaria es compuesta (`groupId`, `userId`) para
 * poder cachear miembros de varios grupos a la vez.
 */
@Entity(tableName = "group_members", primaryKeys = ["groupId", "userId"])
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val displayName: String,
    val email: String,
    val role: String,
    val status: String,
    val joinedAt: String?,
)
