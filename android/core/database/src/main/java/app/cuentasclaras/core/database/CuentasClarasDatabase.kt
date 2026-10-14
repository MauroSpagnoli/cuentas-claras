package app.cuentasclaras.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.cuentasclaras.core.database.dao.UserDao
import app.cuentasclaras.core.database.entity.UserEntity

@Database(
    entities = [UserEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CuentasClarasDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
}
