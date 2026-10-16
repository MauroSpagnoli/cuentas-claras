package app.cuentasclaras.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.database.entity.ExpenseSyncStateEntity
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import app.cuentasclaras.core.database.entity.OutboxEntity
import app.cuentasclaras.core.database.migration.MIGRATION_1_2
import app.cuentasclaras.core.database.migration.MIGRATION_2_3
import app.cuentasclaras.core.database.migration.MIGRATION_3_4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** Hash de identidad del esquema v2 (`core/database/schemas/.../2.json`). */
    private val version2IdentityHash = "475f8d7a166a8cf01460a1122358cfe6"

    /** Hash de identidad del esquema v3 (`core/database/schemas/.../3.json`). */
    private val version3IdentityHash = "204562e773d5c551f1643147efa7b63d"

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

    @Test
    fun `migracion 2 a 3 incorpora los gastos y conserva usuario y grupo`() = runTest {
        createVersion2Database().close()

        val database = openMigratedDatabase()
        try {
            assertEquals("demo@example.com", database.userDao().current()?.email)
            assertEquals("Piso", database.groupDao().observeById("g-1").first()?.name)

            val dao = database.expenseDao()
            dao.upsert(sampleExpense())
            dao.upsertShares(
                listOf(ExpenseShareEntity("e-1", "u-1", 4250, null, null)),
            )
            dao.upsertSyncState(ExpenseSyncStateEntity("g-1", "cursor-1", false))

            assertEquals("Cena", dao.observeById("e-1").first()?.description)
            assertEquals(1, dao.observeShares("e-1").first().size)
            assertEquals("cursor-1", dao.syncState("g-1")?.nextCursor)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migracion 3 a 4 incorpora el outbox y la marca de pendiente`() = runTest {
        createVersion3Database().close()

        val database = openMigratedDatabase()
        try {
            assertEquals("demo@example.com", database.userDao().current()?.email)

            // El gasto previo sobrevive y arranca sin operación pendiente.
            val expense = database.expenseDao().observeById("e-1").first()
            assertEquals("Cena", expense?.description)
            assertNull(expense?.pendingOperation)

            // La cola del outbox es usable tras la migración.
            database.outboxDao().enqueue(sampleOutbox())
            assertEquals(1, database.outboxDao().countPending())
            assertEquals("op-1", database.outboxDao().pending(10).single().id)
        } finally {
            database.close()
        }
    }

    private fun sampleOutbox(): OutboxEntity = OutboxEntity(
        id = "op-1",
        entityType = OutboxEntity.ENTITY_EXPENSE,
        entityId = "e-1",
        groupId = "g-1",
        operation = OutboxEntity.OPERATION_UPDATE,
        payload = "{}",
        status = OutboxEntity.STATUS_PENDING,
        createdAt = 1L,
    )

    private fun sampleExpense(): ExpenseEntity = ExpenseEntity(
        id = "e-1",
        groupId = "g-1",
        description = "Cena",
        amountMinor = 4250,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = "EQUAL",
        category = null,
        expenseDate = "2026-02-01",
        notes = null,
        createdBy = "u-1",
        createdAt = "2026-02-01T21:00:00Z",
        version = 1,
    )

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
                        createVersion1Schema(db)
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    /** Esquema m��nimo de la v1 (solo el usuario) mǭs la tabla de identidad de Room. */
    private fun createVersion1Schema(db: SupportSQLiteDatabase) {
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

    /**
     * Crea una base de datos en la versi��n 2 con un usuario y un grupo previos,
     * aplicando la migraci��n 1 ��' 2 a mano antes de entregarla.
     */
    private fun createVersion2Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion1Schema(db)
                        db.execSQL(
                            "INSERT INTO current_user " +
                                "(id, email, displayName, defaultCurrency, locale, createdAt) " +
                                "VALUES ('u-1', 'demo@example.com', 'Demo', 'EUR', NULL, NULL)",
                        )
                        MIGRATION_1_2.migrate(db)
                        db.execSQL(
                            "INSERT INTO `groups` " +
                                "(id, name, description, currency, inviteCode, memberCount, createdAt, version) " +
                                "VALUES ('g-1', 'Piso', NULL, 'EUR', NULL, 1, '2026-01-02T10:00:00Z', 0)",
                        )
                        db.execSQL(
                            "UPDATE room_master_table SET identity_hash = '$version2IdentityHash' WHERE id = 42",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    /**
     * Crea una base de datos en la versión 3 con un usuario, un grupo y un gasto
     * previos, aplicando a mano las migraciones 1 → 2 y 2 → 3 antes de entregarla.
     */
    private fun createVersion3Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion1Schema(db)
                        db.execSQL(
                            "INSERT INTO current_user " +
                                "(id, email, displayName, defaultCurrency, locale, createdAt) " +
                                "VALUES ('u-1', 'demo@example.com', 'Demo', 'EUR', NULL, NULL)",
                        )
                        MIGRATION_1_2.migrate(db)
                        db.execSQL(
                            "INSERT INTO `groups` " +
                                "(id, name, description, currency, inviteCode, memberCount, createdAt, version) " +
                                "VALUES ('g-1', 'Piso', NULL, 'EUR', NULL, 1, '2026-01-02T10:00:00Z', 0)",
                        )
                        MIGRATION_2_3.migrate(db)
                        db.execSQL(
                            "INSERT INTO `expenses` " +
                                "(id, groupId, description, amountMinor, currency, paidByUserId, splitType, " +
                                "category, expenseDate, notes, createdBy, createdAt, version) " +
                                "VALUES ('e-1', 'g-1', 'Cena', 4250, 'EUR', 'u-1', 'EQUAL', NULL, " +
                                "'2026-02-01', NULL, 'u-1', '2026-02-01T21:00:00Z', 1)",
                        )
                        db.execSQL(
                            "UPDATE room_master_table SET identity_hash = '$version3IdentityHash' WHERE id = 42",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
