package app.cuentasclaras.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import app.cuentasclaras.core.database.migration.MIGRATION_1_2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifica la migración 1 → 2: se crea a mano una base de datos v1 con el
 * esquema y el hash de identidad que exportó Room, se abre con Room v2 y la
 * migración; Room valida la estructura resultante y aquí comprobamos además que
 * los datos previos sobreviven y que las tablas nuevas son usables.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    /** Hash de identidad del esquema v1 (`core/database/schemas/.../1.json`). */
    private val version1IdentityHash = "350fc88d81355d9eda7d7e6ab7926104"

    @Before
    fun deleteBefore() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun deleteAfter() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migracion 1 a 2 conserva el usuario y crea las tablas de grupos`() = runTest {
        createVersion1Database().apply {
            execSQL(
                "INSERT INTO current_user (id, email, displayName, defaultCurrency, locale, createdAt) " +
                    "VALUES ('u-1', 'demo@example.com', 'Demo', 'EUR', NULL, '2026-01-01T00:00:00Z')",
            )
        }.close()

        val database = openMigratedDatabase()
        try {
            assertEquals("demo@example.com", database.userDao().current()?.email)

            val groupDao = database.groupDao()
            groupDao.upsert(sampleGroup())
            groupDao.upsertSyncState(GroupSyncStateEntity(nextCursor = "cursor-1", endReached = false))

            assertEquals("Piso", groupDao.observeById("g-1").first()?.name)
            assertEquals("cursor-1", groupDao.syncState()?.nextCursor)
            assertEquals(0, groupDao.observeMembers("g-1").first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `reabrir la base migrada no pierde datos`() = runTest {
        createVersion1Database().apply {
            execSQL(
                "INSERT INTO current_user (id, email, displayName, defaultCurrency, locale, createdAt) " +
                    "VALUES ('u-1', 'demo@example.com', 'Demo', 'EUR', NULL, NULL)",
            )
        }.close()

        val first = openMigratedDatabase()
        try {
            first.groupDao().upsert(sampleGroup())
        } finally {
            first.close()
        }

        val reopened = openMigratedDatabase()
        try {
            assertEquals("demo@example.com", reopened.userDao().current()?.email)
            assertEquals("Piso", reopened.groupDao().observeById("g-1").first()?.name)
        } finally {
            reopened.close()
        }
    }

    private fun sampleGroup(): GroupEntity = GroupEntity(
        id = "g-1",
        name = "Piso",
        description = null,
        currency = "EUR",
        inviteCode = null,
        memberCount = 1,
        createdAt = "2026-01-02T10:00:00Z",
        version = 0,
    )

    private fun createVersion1Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `current_user` (" +
                                "`id` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                                "`defaultCurrency` TEXT NOT NULL, `locale` TEXT, `createdAt` TEXT, " +
                                "PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table " +
                                "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
                        )
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                                "VALUES (42, '$version1IdentityHash')",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun openMigratedDatabase(): CuentasClarasDatabase =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CuentasClarasDatabase::class.java,
            DB_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
