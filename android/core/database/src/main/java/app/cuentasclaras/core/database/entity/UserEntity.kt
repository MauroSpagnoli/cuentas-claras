package app.cuentasclaras.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caché del perfil del usuario autenticado. Se guarda una única fila; permite
 * restaurar la sesión (y mostrar el perfil) sin conexión.
 */
@Entity(tableName = "current_user")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val defaultCurrency: String,
    val locale: String?,
    val createdAt: String?,
)
