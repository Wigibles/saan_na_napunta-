package com.saannapunta.app.data

import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.data.model.UpcomingBill
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class BillReminderStage {
    UPCOMING,
    DUE_TODAY,
    OVERDUE
}

data class BillReminderDecision(
    val billId: Long,
    val stage: BillReminderStage,
    val daysUntilDue: Int,
    val newLastRemindedFor: String,
    val notificationTitle: String,
    val notificationBody: String
)

object DailyHabitEngine {

    val PRESET_AMOUNTS_CENTAVOS = listOf(20_00L, 50_00L, 100_00L)

    val DEFAULT_QUICK_CATEGORIES = listOf(
        "Food & Dining",
        "Transportation",
        "Groceries",
        "Personal"
    )

    private val EXCLUDED_QUICK_CATEGORIES_LOWER = setOf(
        "adjustment",
        "credit card payment",
        "transfer"
    )

    /**
     * Returns the user's [limit] most recent distinct expense categories, padded with
     * configured budget categories and default categories so there are always up to [limit] chips.
     */
    fun getRecentExpenseCategories(
        transactions: List<TransactionItem>,
        budgets: List<CategoryBudget> = emptyList(),
        limit: Int = 4
    ): List<String> {
        if (limit <= 0) return emptyList()
        val result = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()

        fun tryAdd(cat: String) {
            val trimmed = cat.trim()
            if (trimmed.isEmpty()) return
            val lower = trimmed.lowercase()
            if (lower in EXCLUDED_QUICK_CATEGORIES_LOWER) return
            if (lower !in seenLower) {
                seenLower.add(lower)
                result.add(trimmed)
            }
        }

        val sortedExpenses = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sortedByDescending { it.timestamp }

        for (tx in sortedExpenses) {
            if (result.size >= limit) break
            if (!tx.category.equals("Uncategorized", ignoreCase = true)) {
                tryAdd(tx.category)
            }
        }

        for (b in budgets) {
            if (result.size >= limit) break
            tryAdd(b.category)
        }

        for (def in DEFAULT_QUICK_CATEGORIES) {
            if (result.size >= limit) break
            tryAdd(def)
        }

        return result.take(limit)
    }

    /**
     * Returns the category most frequently used in EXPENSE transactions with [amountCentavos].
     * Ties are broken by the most recent transaction timestamp. Falls back to the first category
     * in [fallbackCategories] (or `"Food & Dining"`).
     */
    fun getMostFrequentCategoryForAmount(
        transactions: List<TransactionItem>,
        amountCentavos: Long,
        fallbackCategories: List<String> = DEFAULT_QUICK_CATEGORIES
    ): String {
        val matchingExpenses = transactions.filter { tx ->
            tx.type == TransactionType.EXPENSE &&
                tx.amountCentavos == amountCentavos &&
                tx.category.isNotBlank() &&
                tx.category.lowercase() !in EXCLUDED_QUICK_CATEGORIES_LOWER &&
                !tx.category.equals("Uncategorized", ignoreCase = true)
        }

        if (matchingExpenses.isNotEmpty()) {
            val grouped = matchingExpenses.groupBy { it.category.trim().lowercase() }
            val bestGroup = grouped.values.maxWithOrNull(
                compareBy<List<TransactionItem>> { it.size }
                    .thenBy { group -> group.maxOf { it.timestamp } }
            )
            if (!bestGroup.isNullOrEmpty()) {
                return bestGroup.maxBy { it.timestamp }.category.trim()
            }
        }

        return fallbackCategories.firstOrNull {
            it.isNotBlank() && !it.equals("Uncategorized", ignoreCase = true)
        } ?: "Food & Dining"
    }

    /**
     * Finds the user's active Cash account, or null if none exists yet.
     */
    fun findCashAccount(accounts: List<Account>): Account? {
        val active = accounts.filter { it.archivedAt == null }
        return active.firstOrNull { it.type == AccountType.CASH }
            ?: active.firstOrNull { it.provider.equals("cash", ignoreCase = true) }
            ?: active.firstOrNull {
                it.name.equals("Cash", ignoreCase = true) ||
                    it.name.equals("Cash on Hand", ignoreCase = true)
            }
    }

    /**
     * Evaluates whether [bill] should trigger a reminder on [todayDate] (in `Asia/Manila`).
     *
     * Rules:
     * - Remind a configurable [daysBefore] days before (default 3) and on the due day (`daysUntilDue == 0`).
     * - For overdue bills (`daysUntilDue < 0`), send one reminder when first overdue and then weekly (every 7 days).
     * - Never repeat a reminder already recorded in [UpcomingBill.lastRemindedFor] for the same stage/week.
     */
    fun shouldRemindBill(
        bill: UpcomingBill,
        todayDate: LocalDate,
        daysBefore: Int = 3,
        zone: ZoneId = CycleCalculator.MANILA_ZONE
    ): BillReminderDecision? {
        if (bill.isPaid) return null

        val dueDate = CycleCalculator.toLocalDate(bill.dueDateTimestamp, zone)
        val daysUntilDue = ChronoUnit.DAYS.between(todayDate, dueDate).toInt()
        val effectiveDaysBefore = daysBefore.coerceIn(0, 30)

        val dueDateKey = dueDate.toString()
        val todayKey = todayDate.toString()
        val lastReminded = bill.lastRemindedFor?.trim().orEmpty()
        val amountFormatted = MoneyFormatter.format(bill.amountCentavos, false)

        return when {
            daysUntilDue > effectiveDaysBefore -> null

            daysUntilDue in 1..effectiveDaysBefore -> {
                if (lastReminded.startsWith("$dueDateKey:BEFORE") ||
                    lastReminded.startsWith("$dueDateKey:DUE") ||
                    lastReminded == dueDateKey
                ) {
                    null
                } else {
                    val dayWord = if (daysUntilDue == 1) "tomorrow" else "in $daysUntilDue days"
                    BillReminderDecision(
                        billId = bill.id,
                        stage = BillReminderStage.UPCOMING,
                        daysUntilDue = daysUntilDue,
                        newLastRemindedFor = "$dueDateKey:BEFORE:$todayKey",
                        notificationTitle = "Upcoming bill: ${bill.title}",
                        notificationBody = "$amountFormatted is due $dayWord. Tap to review or mark as paid."
                    )
                }
            }

            daysUntilDue == 0 -> {
                if (lastReminded.startsWith("$dueDateKey:DUE") || lastReminded.endsWith(":$todayKey")) {
                    null
                } else {
                    BillReminderDecision(
                        billId = bill.id,
                        stage = BillReminderStage.DUE_TODAY,
                        daysUntilDue = 0,
                        newLastRemindedFor = "$dueDateKey:DUE:$todayKey",
                        notificationTitle = "Due today: ${bill.title}",
                        notificationBody = "$amountFormatted is due today. Tap to pick an account and pay."
                    )
                }
            }

            else -> {
                // Overdue (daysUntilDue < 0): send one reminder and then weekly (every 7 days)
                val overduePrefix = "$dueDateKey:OVERDUE:"
                if (lastReminded.startsWith(overduePrefix)) {
                    val lastDateStr = lastReminded.removePrefix(overduePrefix).trim()
                    val lastRemindedDate = runCatching { LocalDate.parse(lastDateStr) }.getOrNull()
                    if (lastRemindedDate != null) {
                        val daysSinceLast = ChronoUnit.DAYS.between(lastRemindedDate, todayDate)
                        if (daysSinceLast < 7L) {
                            return null
                        }
                    } else {
                        return null
                    }
                }
                val overdueDays = abs(daysUntilDue)
                val overdueLabel = if (overdueDays == 1) "1 day overdue" else "$overdueDays days overdue"
                BillReminderDecision(
                    billId = bill.id,
                    stage = BillReminderStage.OVERDUE,
                    daysUntilDue = daysUntilDue,
                    newLastRemindedFor = "$overduePrefix$todayKey",
                    notificationTitle = "Overdue bill: ${bill.title}",
                    notificationBody = "$amountFormatted is $overdueLabel. Tap to pay from an account."
                )
            }
        }
    }

    /**
     * Computes the initial delay in milliseconds from [nowMillis] to the next 8:00 AM in `Asia/Manila`.
     */
    fun computeInitialDelayToNext8AmManila(
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = CycleCalculator.MANILA_ZONE
    ): Long {
        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var target8Am = nowZdt.toLocalDate().atTime(8, 0).atZone(zone)
        if (!nowZdt.isBefore(target8Am)) {
            target8Am = target8Am.plusDays(1)
        }
        return Duration.between(nowZdt, target8Am).toMillis().coerceAtLeast(60_000L)
    }
}

