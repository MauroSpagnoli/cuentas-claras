package app.cuentasclaras.core.database.di

import android.content.Context
import androidx.room.Room
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.dao.ExpenseDao
import app.cuentasclaras.core.database.dao.GroupDao
import app.cuentasclaras.core.database.dao.UserDao
import app.cuentasclaras.core.database.migration.DATABASE_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CuentasClarasDatabase =
        Room.databaseBuilder(context, CuentasClarasDatabase::class.java, "cuentas-claras.db")
            .addMigrations(*DATABASE_MIGRATIONS)
            .build()

    @Provides
    fun provideUserDao(database: CuentasClarasDatabase): UserDao = database.userDao()

    @Provides
    fun provideGroupDao(database: CuentasClarasDatabase): GroupDao = database.groupDao()

    @Provides
    fun provideExpenseDao(database: CuentasClarasDatabase): ExpenseDao = database.expenseDao()
}
