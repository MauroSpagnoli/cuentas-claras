package app.cuentasclaras.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.cuentasclaras.core.database.dao.ExpenseDao
import app.cuentasclaras.core.database.dao.GroupDao
import app.cuentasclaras.core.database.dao.OutboxDao
import app.cuentasclaras.core.database.dao.UserDao
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.database.entity.ExpenseSyncStateEntity
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupMemberEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import app.cuentasclaras.core.database.entity.OutboxEntity
import app.cuentasclaras.core.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        GroupSyncStateEntity::class,
        ExpenseEntity::class,
        ExpenseShareEntity::class,
        ExpenseSyncStateEntity::class,
        OutboxEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class CuentasClarasDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun groupDao(): GroupDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun outboxDao(): OutboxDao
}
