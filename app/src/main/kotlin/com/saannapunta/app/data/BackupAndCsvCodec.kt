package com.saannapunta.app.data

import com.saannapunta.app.data.model.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BackupImportResult(
    val success: Boolean,
    val version: Int = 0,
    val importedAccounts: Int = 0,
    val importedTransactions: Int = 0,
    val skippedOrphanedTransactions: Int = 0,
    val skippedTransactionTitles: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class BackupRestoreData(
    val version: Int,
    val accounts: List<Account>,
    val transactions: List<TransactionItem>,
    val budgets: List<CategoryBudget>?,
    val goals: List<SavingsGoal>?,
    val bills: List<UpcomingBill>?,
    val settings: Map<String, String>?
)

object BackupAndCsvCodec {

    const val BACKUP_VERSION = 3

    private val CSV_DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val RAW_SMS_NOTE_REGEX = Regex(
        """(?i)(?:\b(?:PHP|Php|php)\s*[\d,]+|\u20B1\s*[\d,]+|\bref(?:\.|erence)?\s*(?:no\.?|id|#)?\s*[:.]?\s*[A-Za-z0-9]{4,}|\byou\s+(?:have\s+)?(?:paid|sent|received)\b|\b(?:was\s+charged|has\s+been\s+debited|new\s+balance\s+is|available\s+balance|ending\s+in\s+\d{4})\b)"""
    )

    fun looksLikeRawSmsNote(note: String?, sourceInfo: String): Boolean {
        if (note.isNullOrBlank()) return false
        val trimmed = note.trim()
        if (RAW_SMS_NOTE_REGEX.containsMatchIn(trimmed)) {
            val hasPaymentPhrase = Regex(
                """(?i)\b(?:you\s+(?:have\s+)?(?:paid|sent|received)|was\s+charged|has\s+been\s+debited|purchase\s+of|atm\s+withdrawal|new\s+balance|ref(?:\.|erence)?\s*(?:no|id|#))\b"""
            ).containsMatchIn(trimmed)
            if (hasPaymentPhrase) return true
        }
        val isAutoSource = !sourceInfo.equals("Manual", ignoreCase = true) &&
            !sourceInfo.equals("Balance sync", ignoreCase = true)
        if (isAutoSource && RAW_SMS_NOTE_REGEX.containsMatchIn(trimmed)) {
            return true
        }
        return false
    }

    /**
     * Quotes a CSV text field and prefixes values starting with `=`, `+`, `-`, or `@`
     * with a single quote `'` to prevent spreadsheet formula injection.
     */
    fun escapeCsvTextField(raw: String): String {
        val formulaTriggers = charArrayOf('=', '+', '-', '@')
        val leadingNonSpace = raw.trimStart().firstOrNull()
        val formulaSafe = if (
            raw.isNotEmpty() &&
            (raw[0] in formulaTriggers || (leadingNonSpace != null && leadingNonSpace in formulaTriggers))
        ) {
            "'$raw"
        } else {
            raw
        }
        val escapedQuotes = formulaSafe.replace("\"", "\"\"")
        return "\"$escapedQuotes\""
    }

    fun formatCentavosForCsv(amountCentavos: Long): String {
        val sign = if (amountCentavos < 0) "-" else ""
        val absVal = kotlin.math.abs(amountCentavos)
        val pesos = absVal / 100L
        val centavos = absVal % 100L
        return String.format(Locale.US, "%s%d.%02d", sign, pesos, centavos)
    }

    /**
     * Exports transactions as CSV with every text field quoted, formula-injection prefixes neutralized
     * with a leading `'`, and raw SMS messages dropped from the Notes column by default.
     */
    fun exportTransactionsCsv(
        transactions: List<TransactionItem>,
        accounts: List<Account>,
        includeRawSmsNotes: Boolean = false
    ): String {
        val accMap = accounts.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("ID,Date,Title,Type,Category,Amount_PHP,Account,Notes,Source\n")
        for (tx in transactions) {
            val dateStr = Instant.ofEpochMilli(tx.timestamp)
                .atZone(CycleCalculator.MANILA_ZONE)
                .format(CSV_DATE_FMT)
            val pesos = formatCentavosForCsv(tx.amountCentavos)
            val accName = accMap[tx.accountId]?.name ?: "Unknown"
            val noteText = when {
                tx.note.isNullOrBlank() -> ""
                !includeRawSmsNotes && looksLikeRawSmsNote(tx.note, tx.sourceInfo) -> ""
                else -> tx.note
            }
            val row = listOf(
                tx.id.toString(),
                escapeCsvTextField(dateStr),
                escapeCsvTextField(tx.title),
                escapeCsvTextField(tx.type.name),
                escapeCsvTextField(tx.category),
                pesos,
                escapeCsvTextField(accName),
                escapeCsvTextField(noteText),
                escapeCsvTextField(tx.sourceInfo)
            ).joinToString(",")
            sb.append(row).append('\n')
        }
        return sb.toString()
    }

    /**
     * Serializes full-fidelity Backup JSON v3 with stable account IDs and ID-based account references
     * on transactions, while keeping `account` / `targetAccount` names for readability.
     * Does NOT modify `last_backup_date`.
     */
    fun exportBackupJsonV3(
        accounts: List<Account>,
        transactions: List<TransactionItem>,
        budgets: List<CategoryBudget>,
        goals: List<SavingsGoal>,
        bills: List<UpcomingBill>,
        payCycleConfig: PayCycleConfig,
        userName: String,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val nameById = accounts.associate { it.id to it.name }

        val accList = accounts.map { a ->
            linkedMapOf<String, Any?>(
                "id" to a.id,
                "name" to a.name,
                "provider" to a.provider,
                "type" to a.type.name,
                "accountNumberMasked" to a.accountNumberMasked,
                "balanceCentavos" to a.balanceCentavos,
                "isSpendable" to a.isSpendable,
                "iconResName" to (a.iconResName ?: ""),
                "balanceAsOf" to a.balanceAsOf,
                "needsOpeningBalance" to a.needsOpeningBalance,
                "archivedAt" to a.archivedAt
            )
        }

        val txList = transactions.map { t ->
            linkedMapOf<String, Any?>(
                "id" to t.id,
                "title" to t.title,
                "amountCentavos" to t.amountCentavos,
                "type" to t.type.name,
                "accountId" to t.accountId,
                "targetAccountId" to t.targetAccountId,
                "account" to (nameById[t.accountId] ?: ""),
                "targetAccount" to (t.targetAccountId?.let { nameById[it] } ?: ""),
                "category" to t.category,
                "subCategory" to (t.subCategory ?: ""),
                "timestamp" to t.timestamp,
                "note" to (t.note ?: ""),
                "sourceInfo" to t.sourceInfo,
                "refNumber" to (t.refNumber ?: ""),
                "dedupKey" to (t.dedupKey ?: "")
            )
        }

        val budgetList = budgets.map { b ->
            linkedMapOf<String, Any?>(
                "category" to b.category,
                "limitCentavos" to b.limitCentavos,
                "examples" to b.examples,
                "iconName" to b.iconName,
                "colorHex" to b.colorHex,
                "subCategories" to if (b.subCategories.isNotEmpty()) b.subCategories else null
            )
        }

        val goalList = goals.map { g ->
            linkedMapOf<String, Any?>(
                "name" to g.name,
                "targetCentavos" to g.targetCentavos,
                "savedCentavos" to g.savedCentavos,
                "accountId" to g.accountId
            )
        }

        val billList = bills.map { b ->
            linkedMapOf<String, Any?>(
                "title" to b.title,
                "amountCentavos" to b.amountCentavos,
                "dueDateTimestamp" to b.dueDateTimestamp,
                "recurring" to b.recurring,
                "category" to b.category,
                "subCategory" to (b.subCategory ?: ""),
                "anchorDayOfMonth" to b.anchorDayOfMonth,
                "lastRemindedFor" to (b.lastRemindedFor ?: "")
            )
        }

        val settingsMap = linkedMapOf<String, Any?>(
            "user_name" to userName,
            "pay_cycle_type" to payCycleConfig.type.name,
            "first_payday" to payCycleConfig.firstPayday,
            "second_payday" to payCycleConfig.secondPayday,
            "weekend_rule" to payCycleConfig.payFridayBeforeWeekend
        )

        val root = linkedMapOf<String, Any?>(
            "app" to "SaanNaPunta",
            "version" to BACKUP_VERSION,
            "exportedAt" to exportedAtMillis,
            "accounts" to accList,
            "transactions" to txList,
            "budgets" to budgetList,
            "goals" to goalList,
            "bills" to billList,
            "settings" to settingsMap
        )

        return SimpleJson.stringify(root, indent = 2)
    }

    /**
     * Validates a v3 or v2/v1 backup JSON string and resolves account references.
     * - Version 3 backups match accounts by stable `accountId` / `targetAccountId` (falling back to name only if `accountId` is absent).
     * - Version 2 / 1 backups match accounts by `account` / `targetAccount` name.
     * - Never silently reassigns orphaned transactions to the first account; skips and reports them.
     */
    fun validateAndResolveBackupJson(
        jsonString: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<BackupImportResult, BackupRestoreData?> {
        val root = runCatching { SimpleJson.parseObject(jsonString) }.getOrNull()
            ?: return BackupImportResult(success = false, errorMessage = "Invalid JSON format") to null

        val version = (root["version"] as? Number)?.toInt() ?: 1
        val accRawList = root["accounts"] as? List<*>
            ?: return BackupImportResult(success = false, version = version, errorMessage = "Missing accounts array") to null
        val txRawList = root["transactions"] as? List<*>
            ?: return BackupImportResult(success = false, version = version, errorMessage = "Missing transactions array") to null

        val parsedAccounts = mutableListOf<Account>()
        val idToAssignedId = mutableMapOf<Long, Long>()
        val nameToAssignedId = mutableMapOf<String, Long>()
        var nextSyntheticId = 1L

        for (entry in accRawList) {
            val obj = entry as? Map<*, *>
                ?: return BackupImportResult(success = false, version = version, errorMessage = "Malformed account entry") to null
            val typeStr = obj["type"] as? String
                ?: return BackupImportResult(success = false, version = version, errorMessage = "Account missing type") to null
            val accType = runCatching { AccountType.valueOf(typeStr) }.getOrNull()
                ?: return BackupImportResult(success = false, version = version, errorMessage = "Invalid account type: $typeStr") to null
            val name = (obj["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: return BackupImportResult(success = false, version = version, errorMessage = "Account missing name") to null
            val balNum = obj["balanceCentavos"] as? Number
                ?: return BackupImportResult(success = false, version = version, errorMessage = "Account missing balanceCentavos") to null

            val explicitId = (obj["id"] as? Number)?.toLong()?.takeIf { it > 0L }
            val assignedId = if (explicitId != null && explicitId !in idToAssignedId.values) {
                if (explicitId >= nextSyntheticId) nextSyntheticId = explicitId + 1L
                explicitId
            } else {
                while (nextSyntheticId in idToAssignedId.values) nextSyntheticId++
                nextSyntheticId++
            }
            if (explicitId != null) {
                idToAssignedId[explicitId] = assignedId
            }
            // Keep first non-archived or first seen name mapping for v2 compatibility
            if (name !in nameToAssignedId) {
                nameToAssignedId[name] = assignedId
            }

            val archivedAt = (obj["archivedAt"] as? Number)?.toLong()?.takeIf { it > 0L }
            val account = Account(
                id = assignedId,
                name = name,
                provider = (obj["provider"] as? String)?.ifBlank { "other" } ?: "other",
                type = accType,
                accountNumberMasked = (obj["accountNumberMasked"] as? String) ?: "",
                balanceCentavos = balNum.toLong(),
                isSpendable = (obj["isSpendable"] as? Boolean) ?: (accType != AccountType.LIABILITY && archivedAt == null),
                statusLine = if (archivedAt != null) "Archived account" else "Restored from backup",
                iconResName = (obj["iconResName"] as? String)?.ifBlank { null },
                balanceAsOf = (obj["balanceAsOf"] as? Number)?.toLong() ?: nowMillis,
                needsOpeningBalance = (obj["needsOpeningBalance"] as? Boolean) ?: false,
                archivedAt = archivedAt
            )
            parsedAccounts.add(account)
        }

        val parsedTransactions = mutableListOf<TransactionItem>()
        val skippedTitles = mutableListOf<String>()

        for (entry in txRawList) {
            val obj = entry as? Map<*, *> ?: continue
            val title = (obj["title"] as? String)?.ifBlank { "Transaction" } ?: "Transaction"
            val typeStr = obj["type"] as? String ?: continue
            val type = runCatching { TransactionType.valueOf(typeStr) }.getOrNull() ?: continue
            val amountCentavos = (obj["amountCentavos"] as? Number)?.toLong() ?: 0L

            val hasAccountIdKey = obj.containsKey("accountId") && obj["accountId"] != null
            val resolvedAccountId: Long? = if (hasAccountIdKey) {
                val rawAccId = (obj["accountId"] as? Number)?.toLong()
                if (rawAccId != null) idToAssignedId[rawAccId] else null
            } else {
                val accName = (obj["account"] as? String)?.trim().orEmpty()
                if (accName.isNotEmpty()) nameToAssignedId[accName] else null
            }

            if (resolvedAccountId == null) {
                skippedTitles.add(title)
                continue
            }

            val hasTargetIdKey = obj.containsKey("targetAccountId") && obj["targetAccountId"] != null
            val rawTargetName = (obj["targetAccount"] as? String)?.trim().orEmpty()
            val resolvedTargetId: Long? = when {
                hasTargetIdKey -> {
                    val rawTargetId = (obj["targetAccountId"] as? Number)?.toLong()
                    if (rawTargetId != null && rawTargetId > 0L) {
                        val mapped = idToAssignedId[rawTargetId]
                        if (mapped == null && type == TransactionType.TRANSFER) {
                            skippedTitles.add(title)
                            continue
                        }
                        mapped
                    } else null
                }
                rawTargetName.isNotEmpty() -> {
                    val mapped = nameToAssignedId[rawTargetName]
                    if (mapped == null && type == TransactionType.TRANSFER) {
                        skippedTitles.add(title)
                        continue
                    }
                    mapped
                }
                else -> null
            }

            val tx = TransactionItem(
                id = 0L,
                title = title,
                amountCentavos = amountCentavos,
                type = type,
                accountId = resolvedAccountId,
                targetAccountId = resolvedTargetId,
                category = (obj["category"] as? String)?.ifBlank { "Uncategorized" } ?: "Uncategorized",
                subCategory = (obj["subCategory"] as? String)?.ifBlank { null },
                timestamp = (obj["timestamp"] as? Number)?.toLong() ?: nowMillis,
                note = (obj["note"] as? String)?.ifBlank { null },
                sourceInfo = (obj["sourceInfo"] as? String)?.ifBlank { "Manual" } ?: "Manual",
                refNumber = (obj["refNumber"] as? String)?.ifBlank { null },
                dedupKey = (obj["dedupKey"] as? String)?.ifBlank { null }
            )
            parsedTransactions.add(tx)
        }

        val parsedBudgets = (root["budgets"] as? List<*>)?.mapNotNull { entry ->
            val o = entry as? Map<*, *> ?: return@mapNotNull null
            val cat = (o["category"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val scRaw = o["subCategories"]
            val subCats = when (scRaw) {
                is List<*> -> scRaw.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
                is String -> if (scRaw.isNotBlank()) {
                    (runCatching { SimpleJson.parseArray(scRaw) }.getOrNull() ?: emptyList<Any?>())
                        .mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
                } else emptyList()
                else -> emptyList()
            }
            CategoryBudget(
                category = cat,
                limitCentavos = (o["limitCentavos"] as? Number)?.toLong() ?: 0L,
                examples = (o["examples"] as? String) ?: "",
                iconName = (o["iconName"] as? String)?.ifBlank { null },
                colorHex = (o["colorHex"] as? String)?.ifBlank { null },
                subCategories = subCats
            )
        }

        val parsedGoals = (root["goals"] as? List<*>)?.mapNotNull { entry ->
            val o = entry as? Map<*, *> ?: return@mapNotNull null
            val name = (o["name"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rawGoalAccId = (o["accountId"] as? Number)?.toLong()
            SavingsGoal(
                name = name,
                targetCentavos = (o["targetCentavos"] as? Number)?.toLong() ?: 0L,
                savedCentavos = (o["savedCentavos"] as? Number)?.toLong() ?: 0L,
                accountId = rawGoalAccId?.let { idToAssignedId[it] }
            )
        }

        val parsedBills = (root["bills"] as? List<*>)?.mapNotNull { entry ->
            val o = entry as? Map<*, *> ?: return@mapNotNull null
            val title = (o["title"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val dueTs = (o["dueDateTimestamp"] as? Number)?.toLong() ?: nowMillis
            val anchor = (o["anchorDayOfMonth"] as? Number)?.toInt()
                ?: CycleCalculator.toLocalDate(dueTs).dayOfMonth
            UpcomingBill(
                title = title,
                amountCentavos = (o["amountCentavos"] as? Number)?.toLong() ?: 0L,
                dueDateTimestamp = dueTs,
                isPaid = false,
                recurring = (o["recurring"] as? Boolean) ?: true,
                category = (o["category"] as? String)?.ifBlank { "Utilities" } ?: "Utilities",
                subCategory = (o["subCategory"] as? String)?.ifBlank { null },
                anchorDayOfMonth = anchor,
                lastRemindedFor = (o["lastRemindedFor"] as? String)?.ifBlank { null }
            )
        }

        val parsedSettings = (root["settings"] as? Map<*, *>)?.let { s ->
            linkedMapOf(
                "user_name" to ((s["user_name"] as? String) ?: ""),
                "pay_cycle_type" to ((s["pay_cycle_type"] as? String) ?: PayCycleType.SEMI_MONTHLY_15_30.name),
                "first_payday" to (((s["first_payday"] as? Number)?.toInt() ?: 15).toString()),
                "second_payday" to (((s["second_payday"] as? Number)?.toInt() ?: 30).toString()),
                "weekend_rule" to (((s["weekend_rule"] as? Boolean) ?: true).toString())
            )
        }

        val result = BackupImportResult(
            success = true,
            version = version,
            importedAccounts = parsedAccounts.size,
            importedTransactions = parsedTransactions.size,
            skippedOrphanedTransactions = skippedTitles.size,
            skippedTransactionTitles = skippedTitles
        )
        val restoreData = BackupRestoreData(
            version = version,
            accounts = parsedAccounts,
            transactions = parsedTransactions,
            budgets = parsedBudgets,
            goals = parsedGoals,
            bills = parsedBills,
            settings = parsedSettings
        )
        return result to restoreData
    }
}

/**
 * Minimal, zero-dependency JSON parser and serializer that runs identically on Android and in
 * local JVM JUnit tests without relying on `android.jar` `org.json` stubs.
 */
object SimpleJson {

    fun stringify(value: Any?, indent: Int = 2): String {
        val sb = StringBuilder()
        writeValue(sb, value, indent, currentDepth = 0)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, value: Any?, indent: Int, currentDepth: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Number -> sb.append(value.toString())
            is String -> writeEscapedString(sb, value)
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                val entries = value.entries.toList()
                for (i in entries.indices) {
                    val (k, v) = entries[i]
                    appendIndent(sb, indent * (currentDepth + 1))
                    writeEscapedString(sb, k.toString())
                    sb.append(": ")
                    writeValue(sb, v, indent, currentDepth + 1)
                    if (i < entries.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                appendIndent(sb, indent * currentDepth)
                sb.append('}')
            }
            is Iterable<*> -> {
                val list = value.toList()
                if (list.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                for (i in list.indices) {
                    appendIndent(sb, indent * (currentDepth + 1))
                    writeValue(sb, list[i], indent, currentDepth + 1)
                    if (i < list.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                appendIndent(sb, indent * currentDepth)
                sb.append(']')
            }
            else -> writeEscapedString(sb, value.toString())
        }
    }

    private fun appendIndent(sb: StringBuilder, spaces: Int) {
        repeat(spaces) { sb.append(' ') }
    }

    private fun writeEscapedString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') {
                    sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }

    fun parseObject(json: String): Map<String, Any?>? {
        val parser = Parser(json)
        val v = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) throw IllegalArgumentException("Trailing characters in JSON")
        @Suppress("UNCHECKED_CAST")
        return v as? Map<String, Any?>
    }

    fun parseArray(json: String): List<Any?>? {
        val parser = Parser(json)
        val v = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) throw IllegalArgumentException("Trailing characters in JSON")
        return v as? List<Any?>
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) {
                pos++
            }
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val ch = src[pos]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected character '$ch' at $pos")
            }
        }

        private fun parseObj(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (val sep = peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' but got '$sep' at $pos")
                }
            }
        }

        private fun parseArr(): List<Any?> {
            expect('[')
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (val sep = peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' but got '$sep' at $pos")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                if (ch == '"') return sb.toString()
                if (ch == '\\') {
                    if (pos >= src.length) throw IllegalArgumentException("Unterminated escape in string")
                    when (val esc = src[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("Invalid boolean at $pos")
            }
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalArgumentException("Invalid null at $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val token = src.substring(start, pos)
            return if (isFloat) {
                token.toDouble()
            } else {
                token.toLong()
            }
        }

        private fun peek(): Char? = if (pos < src.length) src[pos] else null

        private fun expect(expected: Char) {
            if (pos >= src.length || src[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at $pos")
            }
            pos++
        }
    }
}
