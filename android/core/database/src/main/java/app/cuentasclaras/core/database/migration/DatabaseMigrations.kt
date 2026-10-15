package app.cuentasclaras.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migración 1 → 2: la caché pasa de solo el usuario a incluir grupos, sus
 * miembros y el cursor de sincronización del listado.
 *
 * El SQL debe coincidir exactamente con el esquema que genera Room (columnas,
 * nulabilidad y clave primaria), o la validación de la migración fallará.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `groups` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "`currency` TEXT NOT NULL, " +
                "`inviteCode` TEXT, " +
                "`memberCount` INTEGER NOT NULL, " +
                "`createdAt` TEXT, " +
                "`version` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_members` (" +
                "`groupId` TEXT NOT NULL, " +
                "`userId` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "`email` TEXT NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`joinedAt` TEXT, " +
                "PRIMARY KEY(`groupId`, `userId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_sync_state` (" +
                "`id` INTEGER NOT NULL, " +
                "`nextCursor` TEXT, " +
                "`endReached` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/**
 * Migración 2 → 3: la caché incorpora los gastos de cada grupo, sus partes y el
 * cursor de sincronización de cada listado de gastos.
 *
 * El SQL debe coincidir exactamente con el esquema que genera Room (columnas,
 * nulabilidad, clave primaria e índices), o la validación de la migración
 * fallará.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expenses` (" +
                "`id` TEXT NOT NULL, " +
                "`groupId` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`paidByUserId` TEXT NOT NULL, " +
                "`splitType` TEXT NOT NULL, " +
                "`category` TEXT, " +
                "`expenseDate` TEXT NOT NULL, " +
                "`notes` TEXT, " +
                "`createdBy` TEXT NOT NULL, " +
                "`createdAt` TEXT, " +
                "`version` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expenses_groupId_expenseDate` " +
                "ON `expenses` (`groupId`, `expenseDate`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expense_shares` (" +
                "`expenseId` TEXT NOT NULL, " +
                "`userId` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`percentage` TEXT, " +
                "`weight` TEXT, " +
                "PRIMARY KEY(`expenseId`, `userId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expense_sync_state` (" +
                "`groupId` TEXT NOT NULL, " +
                "`nextCursor` TEXT, " +
                "`endReached` INTEGER NOT NULL, " +
                "PRIMARY KEY(`groupId`))",
        )
    }
}

/** Todas las migraciones de la base de datos, en orden. */
val DATABASE_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

