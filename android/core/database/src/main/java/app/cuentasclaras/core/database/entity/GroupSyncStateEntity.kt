package app.cuentasclaras.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cursor de sincronización del listado de grupos. Es una fila única (`id = 0`)
 * que guarda el `nextCursor` opaco que devolvió el backend y si ya no hay más
 * páginas. La usa el `RemoteMediator` para pedir la siguiente página.
 */
@Entity(tableName = "group_sync_state")
data class GroupSyncStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val nextCursor: String?,
    val endReached: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
