package app.cuentasclaras.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cursor de sincronización del listado de gastos, una fila por grupo.
 *
 * Se cachean varios grupos a la vez, así que la clave es el `groupId` y no una
 * fila única como en el listado de grupos.
 */
@Entity(tableName = "expense_sync_state")
data class ExpenseSyncStateEntity(
    @PrimaryKey val groupId: String,
    val nextCursor: String?,
    val endReached: Boolean,
)
