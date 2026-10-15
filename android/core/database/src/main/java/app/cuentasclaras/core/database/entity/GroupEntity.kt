package app.cuentasclaras.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Grupo cacheado. `createdAt` es el instante ISO-8601 que usa el backend como
 * clave del cursor, y `version` su contador de concurrencia optimista.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val currency: String,
    val inviteCode: String?,
    val memberCount: Int,
    val createdAt: String?,
    val version: Long,
)
