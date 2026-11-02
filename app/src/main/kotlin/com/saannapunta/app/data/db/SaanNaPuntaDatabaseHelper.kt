package com.saannapunta.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.saannapunta.app.data.model.PayCycleType

class SaanNaPuntaDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    interface SqlExecutor {
        fun execSQL(sql: String)
        fun deleteAll(table: String)
        fun upsertSetting(key: String, value: String)
    }

    companion object {
        const val DATABASE_NAME = "saannapunta.db"
        const val DATABASE_VERSION = 5

        const val TABLE_ACCOUNTS = "accounts"
        const val TABLE_TRANSACTIONS = "transactions"
        const val TABLE_BUDGETS = "category_budgets"
        const val TABLE_GOALS = "savings_goals"
        const val TABLE_UPCOMING = "upcoming_bills"
        const val TABLE_SETTINGS = "settings"
        const val TABLE_INGEST_LOG = "ingest_log"

        val DEFAULT_SETTINGS = mapOf(
            "onboarding_completed" to "false",
            "user_name" to "",
            "pay_cycle_type" to PayCycleType.SEMI_MONTHLY_15_30.name,
            "first_payday" to "15",
            "second_payday" to "30",
            "weekend_rule" to "true",
            "widget_hide_amounts" to "false",
            "widget_one_tap_mode" to "false",
            "bill_reminder_days_before" to "3",
            "ui_sounds_enabled" to "true"
        )

        fun upgradeSchema(executor: SqlExecutor, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                executor.deleteAll(TABLE_TRANSACTIONS)
                executor.deleteAll(TABLE_GOALS)
                executor.deleteAll(TABLE_ACCOUNTS)
                executor.deleteAll(TABLE_BUDGETS)
                executor.deleteAll(TABLE_UPCOMING)
                executor.deleteAll(TABLE_SETTINGS)
                try { executor.execSQL("ALTER TABLE $TABLE_UPCOMING ADD COLUMN recurring INTEGER NOT NULL DEFAULT 1") } catch (_: Exception) {}
                try { executor.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_timestamp ON $TABLE_TRANSACTIONS(timestamp)") } catch (_: Exception) {}
                for ((k, v) in DEFAULT_SETTINGS) {
                    executor.upsertSetting(k, v)
                }
            }
            if (oldVersion < 3) {
                ensureColumnsExist(executor)
            }
            if (oldVersion < 4 && newVersion >= 4) {
                ensureColumnsExist(executor)
            }
            if (oldVersion < 5 && newVersion >= 5) {
                ensureColumnsExist(executor)
            }
        }

        fun ensureColumnsExist(executor: SqlExecutor) {
            try { executor.execSQL("ALTER TABLE $TABLE_UPCOMING ADD COLUMN category TEXT NOT NULL DEFAULT 'Utilities'") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_UPCOMING ADD COLUMN subCategory TEXT") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_UPCOMING ADD COLUMN anchorDayOfMonth INTEGER") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_UPCOMING ADD COLUMN lastRemindedFor TEXT") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_BUDGETS ADD COLUMN iconName TEXT") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_BUDGETS ADD COLUMN colorHex TEXT") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_BUDGETS ADD COLUMN subCategories TEXT") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN subCategory TEXT") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_ACCOUNTS ADD COLUMN balanceAsOf INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_ACCOUNTS ADD COLUMN needsOpeningBalance INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_ACCOUNTS ADD COLUMN archivedAt INTEGER") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_GOALS ADD COLUMN accountId INTEGER") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN refNumber TEXT") } catch (_: Exception) {}
            try { executor.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN dedupKey TEXT") } catch (_: Exception) {}
            try {
                executor.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS $TABLE_INGEST_LOG (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp INTEGER NOT NULL,
                        sender TEXT NOT NULL,
                        bodyHash TEXT NOT NULL,
                        status TEXT NOT NULL,
                        reason TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            } catch (_: Exception) {}
            try { executor.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_ref ON $TABLE_TRANSACTIONS(refNumber)") } catch (_: Exception) {}
            try { executor.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_dedup ON $TABLE_TRANSACTIONS(dedupKey)") } catch (_: Exception) {}
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ACCOUNTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                provider TEXT NOT NULL,
                type TEXT NOT NULL,
                accountNumberMasked TEXT,
                balanceCentavos INTEGER NOT NULL,
                isSpendable INTEGER NOT NULL DEFAULT 1,
                statusLine TEXT,
                iconResName TEXT,
                balanceAsOf INTEGER NOT NULL DEFAULT 0,
                needsOpeningBalance INTEGER NOT NULL DEFAULT 0,
                archivedAt INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TRANSACTIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amountCentavos INTEGER NOT NULL,
                type TEXT NOT NULL,
                accountId INTEGER NOT NULL,
                targetAccountId INTEGER,
                category TEXT NOT NULL,
                subCategory TEXT,
                timestamp INTEGER NOT NULL,
                note TEXT,
                sourceInfo TEXT,
                refNumber TEXT,
                dedupKey TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BUDGETS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL UNIQUE,
                limitCentavos INTEGER NOT NULL,
                examples TEXT,
                iconName TEXT,
                colorHex TEXT,
                subCategories TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_GOALS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                targetCentavos INTEGER NOT NULL,
                savedCentavos INTEGER NOT NULL,
                accountId INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_UPCOMING (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amountCentavos INTEGER NOT NULL,
                dueDateTimestamp INTEGER NOT NULL,
                isPaid INTEGER NOT NULL DEFAULT 0,
                recurring INTEGER NOT NULL DEFAULT 1,
                category TEXT NOT NULL DEFAULT 'Utilities',
                subCategory TEXT,
                anchorDayOfMonth INTEGER,
                lastRemindedFor TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SETTINGS (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_INGEST_LOG (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                sender TEXT NOT NULL,
                bodyHash TEXT NOT NULL,
                status TEXT NOT NULL,
                reason TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_tx_timestamp ON $TABLE_TRANSACTIONS(timestamp)")
        db.execSQL("CREATE INDEX idx_tx_ref ON $TABLE_TRANSACTIONS(refNumber)")
        db.execSQL("CREATE INDEX idx_tx_dedup ON $TABLE_TRANSACTIONS(dedupKey)")

        writeDefaultSettings(db)
    }

    private fun asExecutor(db: SQLiteDatabase): SqlExecutor = object : SqlExecutor {
        override fun execSQL(sql: String) {
            db.execSQL(sql)
        }

        override fun deleteAll(table: String) {
            db.delete(table, null, null)
        }

        override fun upsertSetting(key: String, value: String) {
            val cv = ContentValues().apply {
                put("key", key)
                put("value", value)
            }
            db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        upgradeSchema(asExecutor(db), oldVersion, newVersion)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureColumnsExist(db)
    }

    fun ensureColumnsExist(db: SQLiteDatabase) {
        ensureColumnsExist(asExecutor(db))
    }

    /** Fresh installs start with no data and onboarding not yet completed. */
    fun writeDefaultSettings(db: SQLiteDatabase) {
        for ((k, v) in DEFAULT_SETTINGS) {
            val cv = ContentValues().apply {
                put("key", k)
                put("value", v)
            }
            db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}
