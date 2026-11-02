package com.saannapunta.app.data

import com.saannapunta.app.data.db.SaanNaPuntaDatabaseHelper
import com.saannapunta.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupMigrationAndCsvTest {

    private class FakeV3Database : SaanNaPuntaDatabaseHelper.SqlExecutor {
        val tablesAndColumns = mutableMapOf<String, MutableSet<String>>(
            SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS to mutableSetOf(
                "id", "name", "provider", "type", "accountNumberMasked",
                "balanceCentavos", "isSpendable", "statusLine", "iconResName"
            ),
            SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS to mutableSetOf(
                "id", "title", "amountCentavos", "type", "accountId",
                "targetAccountId", "category", "subCategory", "timestamp", "note", "sourceInfo"
            ),
            SaanNaPuntaDatabaseHelper.TABLE_BUDGETS to mutableSetOf(
                "id", "category", "limitCentavos", "examples", "iconName", "colorHex", "subCategories"
            ),
            SaanNaPuntaDatabaseHelper.TABLE_GOALS to mutableSetOf(
                "id", "name", "targetCentavos", "savedCentavos"
            ),
            SaanNaPuntaDatabaseHelper.TABLE_UPCOMING to mutableSetOf(
                "id", "title", "amountCentavos", "dueDateTimestamp", "isPaid",
                "recurring", "category", "subCategory"
            ),
            SaanNaPuntaDatabaseHelper.TABLE_SETTINGS to mutableSetOf(
                "key", "value"
            )
        )

        val rowsByTable = mutableMapOf<String, MutableList<Map<String, Any?>>>(
            SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS to mutableListOf(
                mapOf("id" to 1L, "name" to "GCash", "provider" to "gcash", "type" to "E_WALLET", "balanceCentavos" to 150_000L)
            ),
            SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS to mutableListOf(
                mapOf("id" to 10L, "title" to "Jollibee", "amountCentavos" to 28_900L, "type" to "EXPENSE", "accountId" to 1L, "category" to "Food & Dining", "timestamp" to 1_700_000_000_000L)
            ),
            SaanNaPuntaDatabaseHelper.TABLE_BUDGETS to mutableListOf(
                mapOf("id" to 1L, "category" to "Food & Dining", "limitCentavos" to 500_000L)
            ),
            SaanNaPuntaDatabaseHelper.TABLE_GOALS to mutableListOf(
                mapOf("id" to 1L, "name" to "Emergency Fund", "targetCentavos" to 1_000_000L, "savedCentavos" to 250_000L)
            ),
            SaanNaPuntaDatabaseHelper.TABLE_UPCOMING to mutableListOf(
                mapOf("id" to 1L, "title" to "Meralco", "amountCentavos" to 210_000L, "dueDateTimestamp" to 1_700_500_000_000L)
            ),
            SaanNaPuntaDatabaseHelper.TABLE_SETTINGS to mutableListOf(
                mapOf("key" to "onboarding_completed", "value" to "true"),
                mapOf("key" to "user_name", "value" to "Juan")
            )
        )

        val indexes = mutableSetOf("idx_tx_timestamp")
        val deletedTables = mutableListOf<String>()

        override fun execSQL(sql: String) {
            val normalized = sql.trim().replace(Regex("\\s+"), " ")
            val alterRegex = Regex("""(?i)^ALTER TABLE (\w+) ADD COLUMN (\w+)\b""")
            val createTableRegex = Regex("""(?i)^CREATE TABLE (?:IF NOT EXISTS )?(\w+)\s*\((.+)\)$""")
            val createIndexRegex = Regex("""(?i)^CREATE INDEX (?:IF NOT EXISTS )?(\w+) ON (\w+)\((\w+)\)$""")

            when {
                alterRegex.containsMatchIn(normalized) -> {
                    val m = alterRegex.find(normalized)!!
                    val table = m.groupValues[1]
                    val column = m.groupValues[2]
                    val cols = tablesAndColumns[table] ?: throw IllegalStateException("Unknown table $table")
                    if (!cols.add(column)) {
                        throw IllegalStateException("Duplicate column $column on $table")
                    }
                }
                createTableRegex.containsMatchIn(normalized) -> {
                    val m = createTableRegex.find(normalized)!!
                    val table = m.groupValues[1]
                    val colsDef = m.groupValues[2].split(",").map { it.trim().substringBefore(" ") }.toMutableSet()
                    tablesAndColumns.putIfAbsent(table, colsDef)
                    rowsByTable.putIfAbsent(table, mutableListOf())
                }
                createIndexRegex.containsMatchIn(normalized) -> {
                    val m = createIndexRegex.find(normalized)!!
                    indexes.add(m.groupValues[1])
                }
            }
        }

        override fun deleteAll(table: String) {
            deletedTables.add(table)
            rowsByTable[table]?.clear()
        }

        override fun upsertSetting(key: String, value: String) {
            val settings = rowsByTable.getOrPut(SaanNaPuntaDatabaseHelper.TABLE_SETTINGS) { mutableListOf() }
            settings.removeAll { it["key"] == key }
            settings.add(mapOf("key" to key, "value" to value))
        }
    }

    @Test
    fun testV3ToV4DatabaseMigrationUpgradesWithoutDataLoss() {
        val v3Db = FakeV3Database()

        // Upgrade from v3 to v4
        SaanNaPuntaDatabaseHelper.upgradeSchema(v3Db, oldVersion = 3, newVersion = 4)

        // 1. No tables were wiped during v3 -> v4 upgrade; all existing data rows remain intact
        assertTrue(v3Db.deletedTables.isEmpty())
        assertEquals(1, v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS]?.size)
        assertEquals("GCash", v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS]?.single()?.get("name"))
        assertEquals(1, v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS]?.size)
        assertEquals("Jollibee", v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS]?.single()?.get("title"))
        assertEquals(1, v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_BUDGETS]?.size)
        assertEquals(1, v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_GOALS]?.size)
        assertEquals(1, v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_UPCOMING]?.size)
        assertEquals(2, v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_SETTINGS]?.size)

        // 2. All v4 columns, ingest_log table, and dedup indexes were added
        val accCols = v3Db.tablesAndColumns[SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS]!!
        assertTrue(accCols.containsAll(listOf("balanceAsOf", "needsOpeningBalance", "archivedAt")))

        val txCols = v3Db.tablesAndColumns[SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS]!!
        assertTrue(txCols.containsAll(listOf("refNumber", "dedupKey", "subCategory")))

        val goalCols = v3Db.tablesAndColumns[SaanNaPuntaDatabaseHelper.TABLE_GOALS]!!
        assertTrue(goalCols.contains("accountId"))

        val upcomingCols = v3Db.tablesAndColumns[SaanNaPuntaDatabaseHelper.TABLE_UPCOMING]!!
        assertTrue(upcomingCols.contains("anchorDayOfMonth"))

        assertTrue(v3Db.tablesAndColumns.containsKey(SaanNaPuntaDatabaseHelper.TABLE_INGEST_LOG))
        assertTrue(v3Db.indexes.containsAll(listOf("idx_tx_timestamp", "idx_tx_ref", "idx_tx_dedup")))

        // 3. Running ensureColumnsExist again on an already-upgraded v4 DB is safe and idempotent
        SaanNaPuntaDatabaseHelper.ensureColumnsExist(v3Db)
        assertEquals(1, v3Db.rowsByTable[SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS]?.size)
    }

    @Test
    fun testBackupJsonV3StableAccountIdsV2CompatibilityAndOrphanedTransactionsSkipped() {
        val acc1 = Account(
            id = 10L,
            name = "BPI",
            provider = "bpi",
            type = AccountType.BANK,
            accountNumberMasked = "1111",
            balanceCentavos = 500_000L
        )
        val acc2 = Account(
            id = 25L,
            name = "BPI", // Same display name, distinct stable ID!
            provider = "bpi",
            type = AccountType.BANK,
            accountNumberMasked = "7183",
            balanceCentavos = 900_000L
        )
        val txOnSecondAccount = TransactionItem(
            id = 101L,
            title = "Meralco",
            amountCentavos = 231_000L,
            type = TransactionType.EXPENSE,
            accountId = 25L,
            category = "Utilities",
            timestamp = 1_700_000_000_000L,
            refNumber = "REF777",
            dedupKey = "bpi:REF777"
        )

        val v3Json = BackupAndCsvCodec.exportBackupJsonV3(
            accounts = listOf(acc1, acc2),
            transactions = listOf(txOnSecondAccount),
            budgets = listOf(CategoryBudget(id = 1L, category = "Utilities", limitCentavos = 400_000L)),
            goals = listOf(SavingsGoal(id = 1L, name = "Emergency", targetCentavos = 1_000_000L, savedCentavos = 100_000L, accountId = 25L)),
            bills = listOf(UpcomingBill(id = 1L, title = "Internet", amountCentavos = 169_900L, dueDateTimestamp = 1_700_100_000_000L, anchorDayOfMonth = 31)),
            payCycleConfig = PayCycleConfig(),
            userName = "Maria",
            exportedAtMillis = 1_700_000_000_000L
        )

        // 1. Restore v3 JSON: preserves stable account IDs (10 and 25) and links tx to account 25 (not first account 10!)
        val (v3Result, v3Data) = BackupAndCsvCodec.validateAndResolveBackupJson(v3Json)
        assertTrue(v3Result.success)
        assertEquals(3, v3Result.version)
        assertEquals(0, v3Result.skippedOrphanedTransactions)
        assertNotNull(v3Data)
        assertEquals(listOf(10L, 25L), v3Data!!.accounts.map { it.id })
        assertEquals(25L, v3Data.transactions.single().accountId)
        assertEquals("REF777", v3Data.transactions.single().refNumber)
        assertEquals(25L, v3Data.goals?.single()?.accountId)
        assertEquals(31, v3Data.bills?.single()?.anchorDayOfMonth)

        // 2. Restore v2 JSON (name-based accounts, plus an orphaned transaction referencing a non-existent account):
        // Orphaned transaction must be skipped and reported, NEVER reassigned to the first account.
        val v2JsonWithOrphan = """
            {
              "app": "SaanNaPunta",
              "version": 2,
              "exportedAt": 1700000000000,
              "accounts": [
                {
                  "name": "GCash",
                  "provider": "gcash",
                  "type": "E_WALLET",
                  "accountNumberMasked": "",
                  "balanceCentavos": 250000,
                  "isSpendable": true,
                  "iconResName": "logo_gcash"
                }
              ],
              "transactions": [
                {
                  "title": "Valid GCash Expense",
                  "amountCentavos": 15000,
                  "type": "EXPENSE",
                  "account": "GCash",
                  "targetAccount": "",
                  "category": "Food & Dining",
                  "timestamp": 1700000010000,
                  "note": "Lunch",
                  "sourceInfo": "Manual"
                },
                {
                  "title": "Orphaned Maya Expense",
                  "amountCentavos": 99000,
                  "type": "EXPENSE",
                  "account": "Deleted Maya Wallet",
                  "targetAccount": "",
                  "category": "Personal",
                  "timestamp": 1700000020000,
                  "note": "",
                  "sourceInfo": "Manual"
                }
              ]
            }
        """.trimIndent()

        val (v2Result, v2Data) = BackupAndCsvCodec.validateAndResolveBackupJson(v2JsonWithOrphan)
        assertTrue(v2Result.success)
        assertEquals(2, v2Result.version)
        assertEquals(1, v2Result.importedAccounts)
        assertEquals(1, v2Result.importedTransactions)
        assertEquals(1, v2Result.skippedOrphanedTransactions)
        assertEquals(listOf("Orphaned Maya Expense"), v2Result.skippedTransactionTitles)
        assertEquals("Valid GCash Expense", v2Data?.transactions?.single()?.title)
    }

    @Test
    fun testCsvQuotesEveryTextFieldPrefixesFormulaInjectionAndDropsRawSmsByDefault() {
        val account = Account(
            id = 1L,
            name = "GCash",
            provider = "gcash",
            type = AccountType.E_WALLET,
            balanceCentavos = 100_000L
        )
        val formulaTx = TransactionItem(
            id = 1L,
            title = "=cmd|' /C calc'!A0",
            amountCentavos = 12_345L,
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "+InjectedCategory",
            timestamp = 1_700_000_000_000L,
            note = "-100+200",
            sourceInfo = "@attacker"
        )
        val rawSmsTx = TransactionItem(
            id = 2L,
            title = "Jollibee Gapan",
            amountCentavos = 28_900L,
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "Food & Dining",
            timestamp = 1_700_000_000_000L,
            note = "You have paid PHP 289.00 of GCash to JOLLIBEE GAPAN on 11/01/2026 12:45. Your new balance is PHP 14,850.50. Ref. No. 987654321.",
            sourceInfo = "From GCash message"
        )

        val csv = BackupAndCsvCodec.exportTransactionsCsv(
            transactions = listOf(formulaTx, rawSmsTx),
            accounts = listOf(account)
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)

        // Row 1: formula-injection prefixes (=, +, -, @) are prefixed with a single quote inside quotes
        val row1 = lines[1]
        assertTrue(row1.contains("\"'=cmd|' /C calc'!A0\""))
        assertTrue(row1.contains("\"'+InjectedCategory\""))
        assertTrue(row1.contains("\"'-100+200\""))
        assertTrue(row1.contains("\"'@attacker\""))
        assertTrue(row1.contains("\"GCash\""))
        assertTrue(row1.contains("\"EXPENSE\""))
        assertTrue(row1.contains("123.45"))

        // Row 2: raw SMS in note is dropped by default (empty quoted string "")
        val row2 = lines[2]
        assertFalse(row2.contains("Ref. No. 987654321"))
        assertFalse(row2.contains("Your new balance is"))
        assertTrue(row2.endsWith("\"\",\"From GCash message\""))
    }
}

