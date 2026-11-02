package com.saannapunta.app.data

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import com.saannapunta.app.data.db.SaanNaPuntaDatabaseHelper
import com.saannapunta.app.data.model.*
import com.saannapunta.app.parser.SmsMessageParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class MoneyOverview(
    val totalBalanceCentavos: Long = 0,
    val netWorthCentavos: Long = 0,
    val totalBudgetLimitCentavos: Long = 0,
    val totalSpentInCycleCentavos: Long = 0,
    val spentTodayCentavos: Long = 0,
    val leftThisCycleCentavos: Long = 0,
    val safeToSpendPerDayCentavos: Long = 0,
    val dayAllowanceCentavos: Long = 0,
    val leftTodayCentavos: Long = 0,
    val dayAllowanceDate: String = "",
    val isOverBudget: Boolean = false,
    val overspentCentavos: Long = 0,
    val unpaidBillsDueInCycleCentavos: Long = 0,
    val daysLeftInCycle: Int = 14,
    val cycleStartMillis: Long = 0L,
    val cycleEndMillis: Long = 0L,
    val cycleStartDateText: String = "Oct 30",
    val cycleEndDateText: String = "Nov 14",
    val nextPaydayText: String = "Nov 15"
)

/** Everything collected by the onboarding flow, written in one transaction. */
data class OnboardingSetup(
    val name: String,
    val cycleType: PayCycleType,
    val firstPayday: Int,
    val secondPayday: Int,
    val weekendRule: Boolean,
    /** Provider plus opening balance in centavos. */
    val accounts: List<Pair<ProviderInfo, Long>>,
    val budgets: List<CategoryBudget>,
    val goals: List<SavingsGoal>
)

data class RepositorySnapshot(
    val accounts: List<Account> = emptyList(),
    val allAccounts: List<Account> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val upcomingBills: List<UpcomingBill> = emptyList(),
    val possibleTransfers: List<PossibleTransferSuggestion> = emptyList(),
    val overview: MoneyOverview = MoneyOverview()
)

interface MutationStore {
    fun getAccountById(id: Long): Account?
    fun addAccountBalanceDelta(id: Long, storedDeltaCentavos: Long)
    fun archiveAccount(id: Long, archivedAt: Long)
    fun getTransactionById(id: Long): TransactionItem?
    fun insertTransaction(item: TransactionItem): Long
    fun updateTransactionRow(item: TransactionItem)
    fun deleteTransactionRow(id: Long)
    fun getTransfersForAccount(accountId: Long): List<TransactionItem>
    fun getUpcomingBillById(id: Long): UpcomingBill?
    fun updateUpcomingBillState(id: Long, isPaid: Boolean, dueDateTimestamp: Long, anchorDayOfMonth: Int? = null)
    fun getSavingsGoalById(id: Long): SavingsGoal?
    fun updateSavingsGoalRow(goal: SavingsGoal)
    fun getBudgetCategories(): List<String>
}

object CycleCalculator {

    val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")
    private val SHORT_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val PREVIEW_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    fun toLocalDate(epochMillis: Long, zone: ZoneId = MANILA_ZONE): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun startOfDayMillis(date: LocalDate, zone: ZoneId = MANILA_ZONE): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(date: LocalDate, zone: ZoneId = MANILA_ZONE): Long =
        date.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

    fun adjustForWeekendRule(date: LocalDate, payFridayBefore: Boolean): LocalDate {
        if (!payFridayBefore) return date
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.minusDays(1)
            DayOfWeek.SUNDAY -> date.minusDays(2)
            else -> date
        }
    }

    /**
     * Computes `(cycleStartDate, nextPaydayDate)` in `Asia/Manila` using `java.time`.
     * Behavior is identical to the previous Calendar logic for normal cases, including the weekend rule.
     */
    fun calculateActiveCycle(today: LocalDate, config: PayCycleConfig): Pair<LocalDate, LocalDate> {
        if (config.type == PayCycleType.WEEKLY) {
            val targetDow = if (config.firstPayday in Calendar.SUNDAY..Calendar.SATURDAY) {
                if (config.firstPayday == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of(config.firstPayday - 1)
            } else {
                DayOfWeek.MONDAY
            }
            var start = today
            while (start.dayOfWeek != targetDow) {
                start = start.minusDays(1)
            }
            val next = start.plusDays(7)
            return Pair(start, next)
        }

        if (config.type == PayCycleType.FLEXIBLE) {
            val resetDay = config.firstPayday.coerceIn(1, 28)
            val thisMonthStart = today.withDayOfMonth(resetDay)
            return if (!today.isBefore(thisMonthStart)) {
                Pair(thisMonthStart, thisMonthStart.plusMonths(1))
            } else {
                Pair(thisMonthStart.minusMonths(1), thisMonthStart)
            }
        }

        if (config.type == PayCycleType.MONTHLY_END) {
            val prevMonth = YearMonth.from(today).minusMonths(1)
            val thisMonth = YearMonth.from(today)
            val nextMonth = YearMonth.from(today).plusMonths(1)
            val adjPrev = adjustForWeekendRule(prevMonth.atEndOfMonth(), config.payFridayBeforeWeekend)
            val adjThis = adjustForWeekendRule(thisMonth.atEndOfMonth(), config.payFridayBeforeWeekend)
            val adjNext = adjustForWeekendRule(nextMonth.atEndOfMonth(), config.payFridayBeforeWeekend)
            return if (!today.isBefore(adjThis)) {
                Pair(adjThis, adjNext)
            } else {
                Pair(adjPrev, adjThis)
            }
        }

        val p1 = config.firstPayday.coerceIn(1, 28)
        val p2 = config.secondPayday.coerceIn(p1 + 1, 31)
        val currentYm = YearMonth.from(today)
        val candidates = mutableListOf<LocalDate>()
        for (monthOffset in -1L..1L) {
            val ym = currentYm.plusMonths(monthOffset)
            val maxDay = ym.lengthOfMonth()
            val base1 = ym.atDay(p1.coerceAtMost(maxDay))
            val base2 = ym.atDay(p2.coerceAtMost(maxDay))
            candidates.add(adjustForWeekendRule(base1, config.payFridayBeforeWeekend))
            candidates.add(adjustForWeekendRule(base2, config.payFridayBeforeWeekend))
        }
        candidates.sort()

        var startDate = candidates.first()
        var nextDate = candidates.last()
        for (i in 0 until candidates.size - 1) {
            val cStart = candidates[i]
            val cNext = candidates[i + 1]
            if (!today.isBefore(cStart) && today.isBefore(cNext)) {
                startDate = cStart
                nextDate = cNext
                break
            }
        }
        return Pair(startDate, nextDate)
    }

    /**
     * Advances a recurring bill using an anchor day-of-month (`1..31`) so a 31st bill never drifts
     * permanently to the 28th after February. When paying an overdue bill, advances to the next
     * due date strictly after today.
     */
    fun nextRecurringBillDueDate(
        currentDueMillis: Long,
        anchorDayOfMonth: Int?,
        nowMillis: Long,
        zone: ZoneId = MANILA_ZONE
    ): Pair<Long, Int> {
        val currentDueDate = toLocalDate(currentDueMillis, zone)
        val todayDate = toLocalDate(nowMillis, zone)
        val anchorDay = (anchorDayOfMonth ?: currentDueDate.dayOfMonth).coerceIn(1, 31)

        var targetYm = YearMonth.from(currentDueDate).plusMonths(1)
        var candidateDate = targetYm.atDay(anchorDay.coerceAtMost(targetYm.lengthOfMonth()))
        while (!candidateDate.isAfter(todayDate)) {
            targetYm = targetYm.plusMonths(1)
            candidateDate = targetYm.atDay(anchorDay.coerceAtMost(targetYm.lengthOfMonth()))
        }
        return Pair(startOfDayMillis(candidateDate, zone), anchorDay)
    }

    fun previewNextPayday(config: PayCycleConfig, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = MANILA_ZONE): String {
        val today = toLocalDate(nowMillis, zone)
        val (_, next) = calculateActiveCycle(today, config)
        return PREVIEW_DATE_FMT.format(next)
    }

    fun previewDaysLeft(config: PayCycleConfig, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = MANILA_ZONE): Int {
        val today = toLocalDate(nowMillis, zone)
        val (_, next) = calculateActiveCycle(today, config)
        return max(1, ChronoUnit.DAYS.between(today, next).toInt())
    }

    fun computeOverviewSnapshot(
        accList: List<Account>,
        txList: List<TransactionItem>,
        budgetList: List<CategoryBudget>,
        goalsList: List<SavingsGoal>,
        upcomingBills: List<UpcomingBill>,
        cycleConfig: PayCycleConfig,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = MANILA_ZONE,
        storedDayAllowanceDate: String? = null,
        storedDayAllowanceCentavos: Long? = null
    ): MoneyOverview {
        val spendableAccounts = accList.filter {
            it.isSpendable && it.type != AccountType.LIABILITY && !it.needsOpeningBalance && it.archivedAt == null
        }
        val spendableAccountIds = spendableAccounts.map { it.id }.toSet()
        val rawSpendableBalance = spendableAccounts.sumOf { it.balanceCentavos }
        // Savings goals act as earmarks on spendable accounts: they reduce spendable balance
        // without reducing net worth or counting as Personal expenses.
        val earmarkedCentavos = goalsList
            .filter { it.accountId == null || it.accountId in spendableAccountIds }
            .sumOf { it.savedCentavos }
        val spendableBalance = max(0L, rawSpendableBalance - earmarkedCentavos)

        val assets = accList.filter { it.type != AccountType.LIABILITY && it.archivedAt == null }.sumOf { it.balanceCentavos }
        val liabilities = accList.filter { it.type == AccountType.LIABILITY && it.archivedAt == null }.sumOf { it.balanceCentavos }
        val netWorth = assets - liabilities

        val totalBudgetLimit = budgetList.sumOf { it.limitCentavos }

        val todayDate = toLocalDate(nowMillis, zone)
        val todayStart = startOfDayMillis(todayDate, zone)
        val todayEnd = endOfDayMillis(todayDate, zone)

        val (cycleStartDate, nextPaydayDate) = calculateActiveCycle(todayDate, cycleConfig)
        val cycleEndDate = nextPaydayDate.minusDays(1)

        val cycleStartMillis = startOfDayMillis(cycleStartDate, zone)
        val cycleEndMillis = endOfDayMillis(cycleEndDate, zone)

        // Days remaining in cycle (including today)
        val daysLeft = max(1, ChronoUnit.DAYS.between(todayDate, nextPaydayDate).toInt())

        val cycleStartText = SHORT_DATE_FMT.format(cycleStartDate)
        val cycleEndText = SHORT_DATE_FMT.format(cycleEndDate)
        val nextPaydayText = SHORT_DATE_FMT.format(nextPaydayDate)

        // Expenses in current cycle (excluding credit card payments and balance adjustments)
        val cycleExpenses = txList.filter {
            it.isCountableSpend() && it.timestamp in cycleStartMillis..cycleEndMillis
        }
        val totalSpentInCycle = cycleExpenses.sumOf { it.amountCentavos }

        // Spent today (excluding credit card payments and balance adjustments)
        val spentToday = txList
            .filter { it.isCountableSpend() && it.timestamp in todayStart..todayEnd }
            .sumOf { it.amountCentavos }

        // Unpaid bills due on or before cycle end
        val unpaidBillsDueInCycle = upcomingBills
            .filter { !it.isPaid && it.dueDateTimestamp <= cycleEndMillis }
            .sumOf { it.amountCentavos }

        val budgetRemainder = totalBudgetLimit - totalSpentInCycle
        val isOverBudget = totalBudgetLimit > 0L && totalSpentInCycle > totalBudgetLimit
        val overspentCentavos = if (isOverBudget) totalSpentInCycle - totalBudgetLimit else 0L

        val leftThisCycle = if (totalBudgetLimit > 0L) {
            budgetRemainder
        } else {
            spendableBalance
        }

        // Safe to spend per day = (min(budget remainder, spendable balance) - unpaid bills due before cycle end) / daysLeft, floored at 0
        val effectiveCeiling = if (totalBudgetLimit > 0L) {
            minOf(budgetRemainder, spendableBalance)
        } else {
            spendableBalance
        }
        val availableAfterBills = effectiveCeiling - unpaidBillsDueInCycle
        val safePerDay = max(0L, availableAfterBills) / daysLeft

        val todayDateStr = todayDate.toString()
        val dayAllowance = if (storedDayAllowanceDate == todayDateStr && storedDayAllowanceCentavos != null) {
            storedDayAllowanceCentavos
        } else {
            safePerDay
        }
        val leftToday = max(0L, dayAllowance - spentToday)

        return MoneyOverview(
            totalBalanceCentavos = spendableBalance,
            netWorthCentavos = netWorth,
            totalBudgetLimitCentavos = totalBudgetLimit,
            totalSpentInCycleCentavos = totalSpentInCycle,
            spentTodayCentavos = spentToday,
            leftThisCycleCentavos = leftThisCycle,
            safeToSpendPerDayCentavos = safePerDay,
            dayAllowanceCentavos = dayAllowance,
            leftTodayCentavos = leftToday,
            dayAllowanceDate = todayDateStr,
            isOverBudget = isOverBudget,
            overspentCentavos = overspentCentavos,
            unpaidBillsDueInCycleCentavos = unpaidBillsDueInCycle,
            daysLeftInCycle = daysLeft,
            cycleStartMillis = cycleStartMillis,
            cycleEndMillis = cycleEndMillis,
            cycleStartDateText = cycleStartText,
            cycleEndDateText = cycleEndText,
            nextPaydayText = nextPaydayText
        )
    }
}

object MutationEngine {

    const val POSSIBLE_TRANSFER_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
    const val MANUAL_DUPLICATE_WARN_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Moves money into (+) or out of (-) an account. Fails loudly (throws IllegalArgumentException)
     * when [accountId] does not exist so the enclosing database transaction rolls back.
     */
    fun adjustAccount(store: MutationStore, accountId: Long, moneyIn: Long) {
        val account = store.getAccountById(accountId)
            ?: throw IllegalArgumentException("Account with id=$accountId does not exist")
        if (moneyIn == 0L) return
        val isLiability = account.type == AccountType.LIABILITY
        val storedDelta = if (isLiability) -moneyIn else moneyIn
        store.addAccountBalanceDelta(accountId, storedDelta)
    }

    fun applyTransactionBalanceImpact(store: MutationStore, item: TransactionItem, reverse: Boolean) {
        val sign = if (reverse) -1L else 1L
        val amount = item.amountCentavos * sign
        when (item.type) {
            TransactionType.EXPENSE -> adjustAccount(store, item.accountId, -amount)
            TransactionType.INCOME -> adjustAccount(store, item.accountId, amount)
            TransactionType.TRANSFER -> {
                adjustAccount(store, item.accountId, -amount)
                item.targetAccountId?.let { adjustAccount(store, it, amount) }
            }
        }
    }

    /**
     * Reads the current row from the store by [txId], aborts if missing (making a double-tap delete
     * a strict no-op), and reverses the balance impact from the stored row rather than the UI snapshot.
     */
    fun deleteTransaction(store: MutationStore, txId: Long): Boolean {
        val currentRow = store.getTransactionById(txId) ?: return false
        applyTransactionBalanceImpact(store, currentRow, reverse = true)
        store.deleteTransactionRow(currentRow.id)
        return true
    }

    /**
     * Reads the current row from the store by [txId], aborts if missing, reverses the balance impact
     * from the stored row (not the UI snapshot), updates the row, and applies the new balance impact.
     */
    fun updateTransaction(store: MutationStore, txId: Long, newItem: TransactionItem): Boolean {
        val currentRow = store.getTransactionById(txId) ?: return false
        applyTransactionBalanceImpact(store, currentRow, reverse = true)
        val merged = newItem.copy(
            id = currentRow.id,
            refNumber = newItem.refNumber ?: currentRow.refNumber,
            dedupKey = newItem.dedupKey ?: currentRow.dedupKey
        )
        store.updateTransactionRow(merged)
        applyTransactionBalanceImpact(store, merged, reverse = false)
        return true
    }

    /**
     * Reads the current bill from the store by [uiBill.id], aborts if missing, already paid, or
     * already advanced past [uiBill.dueDateTimestamp] (making a double-tap a strict no-op).
     */
    fun markBillPaid(
        store: MutationStore,
        uiBill: UpcomingBill,
        accountId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val currentBill = store.getUpcomingBillById(uiBill.id) ?: return false
        if (currentBill.isPaid) return false
        if (currentBill.recurring && currentBill.dueDateTimestamp != uiBill.dueDateTimestamp) return false

        val budgetCats = store.getBudgetCategories()
        val inferredCategory = SmsMessageParser.detectCategoryForBill(currentBill.title, budgetCats)
        val expense = TransactionItem(
            title = currentBill.title,
            amountCentavos = currentBill.amountCentavos,
            type = TransactionType.EXPENSE,
            accountId = accountId,
            category = currentBill.category.ifBlank { inferredCategory },
            subCategory = currentBill.subCategory,
            timestamp = nowMillis,
            note = if (!currentBill.subCategory.isNullOrBlank()) "Subcategory: ${currentBill.subCategory}" else "Bill payment",
            sourceInfo = "Manual"
        )
        store.insertTransaction(expense)
        applyTransactionBalanceImpact(store, expense, reverse = false)

        if (currentBill.recurring) {
            val (nextDueMillis, anchorDay) = CycleCalculator.nextRecurringBillDueDate(
                currentDueMillis = currentBill.dueDateTimestamp,
                anchorDayOfMonth = currentBill.anchorDayOfMonth,
                nowMillis = nowMillis
            )
            store.updateUpcomingBillState(
                id = currentBill.id,
                isPaid = false,
                dueDateTimestamp = nextDueMillis,
                anchorDayOfMonth = anchorDay
            )
        } else {
            store.updateUpcomingBillState(
                id = currentBill.id,
                isPaid = true,
                dueDateTimestamp = currentBill.dueDateTimestamp,
                anchorDayOfMonth = currentBill.anchorDayOfMonth
            )
        }
        return true
    }

    /**
     * Archives the account (`archivedAt` column) instead of hard-deleting so historical spending,
     * insights and transfers are preserved. Transfers are left untouched: money that moved into a
     * surviving account really is there, so reversing them would corrupt that account's balance.
     */
    fun deleteAccount(
        store: MutationStore,
        accountId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val currentAccount = store.getAccountById(accountId) ?: return false
        if (currentAccount.archivedAt != null) return false

        store.archiveAccount(accountId, nowMillis)
        return true
    }

    /**
     * Treats a savings goal contribution as an earmark on an account: increases [SavingsGoal.savedCentavos]
     * (reducing spendable balance in the overview) without creating an EXPENSE transaction, without
     * reducing net worth, and without counting toward the Personal budget.
     */
    fun contributeToSavingsGoal(
        store: MutationStore,
        goalId: Long,
        amountCentavos: Long,
        fromAccountId: Long?
    ): Boolean {
        if (amountCentavos <= 0L) return false
        val currentGoal = store.getSavingsGoalById(goalId) ?: return false
        if (fromAccountId != null) {
            store.getAccountById(fromAccountId)
                ?: throw IllegalArgumentException("Account with id=$fromAccountId does not exist")
        }
        val updated = currentGoal.copy(
            savedCentavos = currentGoal.savedCentavos + amountCentavos,
            accountId = fromAccountId ?: currentGoal.accountId
        )
        store.updateSavingsGoalRow(updated)
        return true
    }

    /**
     * Detects pairs of INCOME and EXPENSE of the same amount on different accounts within 5 minutes.
     * Never merges automatically; returns suggestions for the user to confirm.
     */
    fun detectPossibleTransfers(
        transactions: List<TransactionItem>,
        dismissedKeys: Set<String> = emptySet()
    ): List<PossibleTransferSuggestion> {
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE &&
                !it.category.equals("Adjustment", ignoreCase = true)
        }
        val incomes = transactions.filter {
            it.type == TransactionType.INCOME &&
                !it.category.equals("Adjustment", ignoreCase = true)
        }

        val usedIncomeIds = mutableSetOf<Long>()
        val suggestions = mutableListOf<PossibleTransferSuggestion>()

        for (exp in expenses) {
            val match = incomes
                .filter { inc ->
                    inc.id !in usedIncomeIds &&
                        inc.accountId != exp.accountId &&
                        inc.amountCentavos == exp.amountCentavos &&
                        abs(inc.timestamp - exp.timestamp) <= POSSIBLE_TRANSFER_WINDOW_MS &&
                        "${exp.id}:${inc.id}" !in dismissedKeys
                }
                .minByOrNull { abs(it.timestamp - exp.timestamp) }

            if (match != null) {
                usedIncomeIds.add(match.id)
                suggestions.add(PossibleTransferSuggestion(expenseTx = exp, incomeTx = match))
            }
        }
        return suggestions
    }

    /**
     * Converts a user-confirmed (EXPENSE, INCOME) pair into a single TRANSFER transaction inside
     * the transaction store.
     */
    fun confirmPossibleTransfer(
        store: MutationStore,
        expenseTxId: Long,
        incomeTxId: Long
    ): Boolean {
        val exp = store.getTransactionById(expenseTxId) ?: return false
        val inc = store.getTransactionById(incomeTxId) ?: return false
        if (exp.type != TransactionType.EXPENSE || inc.type != TransactionType.INCOME) return false
        if (exp.accountId == inc.accountId || exp.amountCentavos != inc.amountCentavos) return false

        applyTransactionBalanceImpact(store, exp, reverse = true)
        applyTransactionBalanceImpact(store, inc, reverse = true)
        store.deleteTransactionRow(exp.id)
        store.deleteTransactionRow(inc.id)

        val transferTx = TransactionItem(
            title = "Transfer (${exp.title} \u2192 ${inc.title})",
            amountCentavos = exp.amountCentavos,
            type = TransactionType.TRANSFER,
            accountId = exp.accountId,
            targetAccountId = inc.accountId,
            category = "Transfer",
            timestamp = minOf(exp.timestamp, inc.timestamp),
            note = exp.note ?: inc.note,
            sourceInfo = exp.sourceInfo,
            refNumber = exp.refNumber ?: inc.refNumber,
            dedupKey = exp.dedupKey ?: inc.dedupKey
        )
        store.insertTransaction(transferTx)
        applyTransactionBalanceImpact(store, transferTx, reverse = false)
        return true
    }

    /**
     * Checks whether a manual transaction entry looks like a duplicate of a recent auto-ingested
     * (SMS or notification) transaction on the same account with the same amount and type.
     */
    fun findSimilarAutoIngestedTransaction(
        accountId: Long,
        amountCentavos: Long,
        type: TransactionType,
        nowMillis: Long,
        existingTransactions: List<TransactionItem>
    ): TransactionItem? {
        if (amountCentavos <= 0L) return null
        return existingTransactions.firstOrNull { tx ->
            val isAutoIngested = !tx.sourceInfo.equals("Manual", ignoreCase = true) &&
                !tx.sourceInfo.equals("Balance sync", ignoreCase = true)
            isAutoIngested &&
                tx.accountId == accountId &&
                tx.amountCentavos == amountCentavos &&
                tx.type == type &&
                abs(tx.timestamp - nowMillis) <= MANUAL_DUPLICATE_WARN_WINDOW_MS
        }
    }
}

class DataRepository private constructor(context: Context) {
    private val dbHelper = SaanNaPuntaDatabaseHelper(context.applicationContext)

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun clearError() {
        _lastError.value = null
    }

    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        runCatching { android.util.Log.e("DataRepository", "Unhandled repository error", throwable) }
        _lastError.value = throwable.message ?: throwable.javaClass.simpleName
    }

    // Single-threaded writer: every DB mutation runs in order, so read-check-write sequences
    // (dedup, balance updates) never interleave between the SMS receiver, the notification
    // listener and the UI.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + dbDispatcher + exceptionHandler)

    private val _stateSnapshot = MutableStateFlow(RepositorySnapshot())
    val stateSnapshot: StateFlow<RepositorySnapshot> = _stateSnapshot.asStateFlow()

    /** Active (non-archived) accounts used by pickers and wallet screens. */
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    /** All accounts including archived ones, for historical transaction attribution. */
    private val _allAccounts = MutableStateFlow<List<Account>>(emptyList())
    val allAccounts: StateFlow<List<Account>> = _allAccounts.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionItem>>(emptyList())
    val transactions: StateFlow<List<TransactionItem>> = _transactions.asStateFlow()

    private val _budgets = MutableStateFlow<List<CategoryBudget>>(emptyList())
    val budgets: StateFlow<List<CategoryBudget>> = _budgets.asStateFlow()

    private val _savingsGoals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    val savingsGoals: StateFlow<List<SavingsGoal>> = _savingsGoals.asStateFlow()

    private val _upcomingBills = MutableStateFlow<List<UpcomingBill>>(emptyList())
    val upcomingBills: StateFlow<List<UpcomingBill>> = _upcomingBills.asStateFlow()

    private val _possibleTransfers = MutableStateFlow<List<PossibleTransferSuggestion>>(emptyList())
    val possibleTransfers: StateFlow<List<PossibleTransferSuggestion>> = _possibleTransfers.asStateFlow()

    /** Persisted in the `dismissed_transfer_pairs` setting; only touched on [dbDispatcher]. */
    private val dismissedTransferPairs = mutableSetOf<String>()

    private val _overview = MutableStateFlow(MoneyOverview())
    val overview: StateFlow<MoneyOverview> = _overview.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    /** null while the database is still loading, so the UI never flashes the wrong screen. */
    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

    private val _payCycleConfig = MutableStateFlow(PayCycleConfig())
    val payCycleConfig: StateFlow<PayCycleConfig> = _payCycleConfig.asStateFlow()

    private val _lastBackupDate = MutableStateFlow("Never")
    val lastBackupDate: StateFlow<String> = _lastBackupDate.asStateFlow()

    private val _biometricLockEnabled = MutableStateFlow(false)
    val biometricLockEnabled: StateFlow<Boolean> = _biometricLockEnabled.asStateFlow()

    private val _widgetHideAmounts = MutableStateFlow(false)
    val widgetHideAmounts: StateFlow<Boolean> = _widgetHideAmounts.asStateFlow()

    private val _widgetOneTapMode = MutableStateFlow(false)
    val widgetOneTapMode: StateFlow<Boolean> = _widgetOneTapMode.asStateFlow()

    private val _uiSoundsEnabled = MutableStateFlow(true)
    val uiSoundsEnabled: StateFlow<Boolean> = _uiSoundsEnabled.asStateFlow()

    private val _billReminderDaysBefore = MutableStateFlow(3)
    val billReminderDaysBefore: StateFlow<Int> = _billReminderDaysBefore.asStateFlow()

    companion object {
        private const val DUPLICATE_BODY_WINDOW_MS = 10 * 60 * 1000L // 10 minutes

        @Volatile
        private var instance: DataRepository? = null

        fun getInstance(context: Context): DataRepository {
            return instance ?: synchronized(this) {
                instance ?: DataRepository(context).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val timerScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_DATE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED
            ) {
                recomputeOverview()
            }
        }
    }

    init {
        refreshAll()
        registerDateAndCycleWatchers()
        runCatching { com.saannapunta.app.reminders.BillReminderScheduler.scheduleDailyCheck(appContext) }
    }

    private fun registerDateAndCycleWatchers() {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            appContext.registerReceiver(dateChangeReceiver, filter)
        }

        // Watch for day and cycle boundary rollover while the app process is alive
        timerScope.launch {
            var lastObservedDate = CycleCalculator.toLocalDate(System.currentTimeMillis())
            while (true) {
                val now = System.currentTimeMillis()
                val todayDate = CycleCalculator.toLocalDate(now)
                val nextMidnightMillis = CycleCalculator.startOfDayMillis(todayDate.plusDays(1))
                val cycleEndMillis = _overview.value.cycleEndMillis
                val nextBoundaryMillis = if (cycleEndMillis > now) {
                    minOf(nextMidnightMillis, cycleEndMillis + 1L)
                } else {
                    nextMidnightMillis
                }
                val sleepMs = (nextBoundaryMillis - now).coerceIn(1_000L, 60_000L)
                delay(sleepMs)

                val afterWake = System.currentTimeMillis()
                val currentDate = CycleCalculator.toLocalDate(afterWake)
                val currentCycleEnd = _overview.value.cycleEndMillis
                if (currentDate != lastObservedDate || (currentCycleEnd in 1 until afterWake)) {
                    lastObservedDate = currentDate
                    recomputeOverview(afterWake)
                }
            }
        }
    }

    fun refreshAll() {
        scope.launch {
            loadFromDb()
        }
    }

    /**
     * Recomputes the active cycle and budget overview (called on app resume, on ACTION_DATE_CHANGED,
     * and when the day or pay-cycle boundary passes).
     */
    fun recomputeOverview(nowMillis: Long = System.currentTimeMillis()) {
        scope.launch {
            val updatedOverview = buildOverview(
                accList = _accounts.value,
                txList = _transactions.value,
                budgetList = _budgets.value,
                goalsList = _savingsGoals.value,
                upcomingBills = _upcomingBills.value,
                cycleConfig = _payCycleConfig.value,
                nowMillis = nowMillis
            )
            _overview.value = updatedOverview
            _stateSnapshot.value = _stateSnapshot.value.copy(overview = updatedOverview)
            runCatching { com.saannapunta.app.widget.SaanNaPuntaWidgetUpdater.requestUpdate(appContext) }
        }
    }

    private fun loadFromDb() {
        val db = dbHelper.readableDatabase

        // 1. Accounts (read all, then separate active vs archived)
        val allAccList = mutableListOf<Account>()
        db.query(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, null, null, null, null, "id ASC").use { accCursor ->
            while (accCursor.moveToNext()) {
                allAccList.add(cursorToAccount(accCursor))
            }
        }
        val activeAccList = allAccList.filter { it.archivedAt == null }

        // 2. Transactions
        val txList = mutableListOf<TransactionItem>()
        db.query(SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS, null, null, null, null, null, "timestamp DESC").use { txCursor ->
            while (txCursor.moveToNext()) {
                txList.add(cursorToTransaction(txCursor))
            }
        }

        // 3. Budgets
        val bList = mutableListOf<CategoryBudget>()
        db.query(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, null, null, null, null, null, "id ASC").use { bCursor ->
            while (bCursor.moveToNext()) {
                bList.add(cursorToBudget(bCursor))
            }
        }

        // 4. Savings Goals
        val gList = mutableListOf<SavingsGoal>()
        db.query(SaanNaPuntaDatabaseHelper.TABLE_GOALS, null, null, null, null, null, "id ASC").use { gCursor ->
            while (gCursor.moveToNext()) {
                gList.add(cursorToGoal(gCursor))
            }
        }

        // 5. Upcoming Bills (unpaid only; recurring bills roll forward when paid)
        val billList = mutableListOf<UpcomingBill>()
        db.query(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, null, "isPaid = 0", null, null, null, "dueDateTimestamp ASC").use { billCursor ->
            while (billCursor.moveToNext()) {
                billList.add(cursorToUpcomingBill(billCursor))
            }
        }

        // 6. Settings
        val settingsMap = mutableMapOf<String, String>()
        db.query(SaanNaPuntaDatabaseHelper.TABLE_SETTINGS, null, null, null, null, null, null).use { sCursor ->
            while (sCursor.moveToNext()) {
                val key = sCursor.getString(sCursor.getColumnIndexOrThrow("key"))
                val value = sCursor.getString(sCursor.getColumnIndexOrThrow("value"))
                settingsMap[key] = value
            }
        }

        val cycleTypeStr = settingsMap["pay_cycle_type"] ?: PayCycleType.SEMI_MONTHLY_15_30.name
        val cycleType = runCatching { PayCycleType.valueOf(cycleTypeStr) }.getOrDefault(PayCycleType.SEMI_MONTHLY_15_30)
        val p1 = settingsMap["first_payday"]?.toIntOrNull() ?: 15
        val p2 = settingsMap["second_payday"]?.toIntOrNull() ?: 30
        val weekend = (settingsMap["weekend_rule"] ?: "true") == "true"
        val newCycleConfig = PayCycleConfig(
            type = cycleType,
            firstPayday = p1,
            secondPayday = p2,
            payFridayBeforeWeekend = weekend
        )

        settingsMap["dismissed_transfer_pairs"]?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) dismissedTransferPairs.add(arr.getString(i))
            }
        }

        val newOverview = buildOverview(activeAccList, txList, bList, gList, billList, newCycleConfig)
        val detectedTransfers = MutationEngine.detectPossibleTransfers(txList, dismissedTransferPairs)

        // Emit all state flows together after all queries and calculations finish, with overview
        // and combined snapshot emitted last so the UI never sees accounts and overview out of sync.
        _userName.value = settingsMap["user_name"] ?: ""
        _lastBackupDate.value = settingsMap["last_backup_date"] ?: "Never"
        _biometricLockEnabled.value = (settingsMap["biometric_lock_enabled"] ?: "false") == "true"
        _widgetHideAmounts.value = (settingsMap["widget_hide_amounts"] ?: "false") == "true"
        _widgetOneTapMode.value = (settingsMap["widget_one_tap_mode"] ?: "false") == "true"
        _uiSoundsEnabled.value = (settingsMap["ui_sounds_enabled"] ?: "true") == "true"
        _billReminderDaysBefore.value = (settingsMap["bill_reminder_days_before"]?.toIntOrNull() ?: 3).coerceIn(0, 30)
        _payCycleConfig.value = newCycleConfig
        _allAccounts.value = allAccList
        _accounts.value = activeAccList
        _transactions.value = txList
        _budgets.value = bList
        _savingsGoals.value = gList
        _upcomingBills.value = billList
        _possibleTransfers.value = detectedTransfers
        _overview.value = newOverview
        _stateSnapshot.value = RepositorySnapshot(
            accounts = activeAccList,
            allAccounts = allAccList,
            transactions = txList,
            budgets = bList,
            savingsGoals = gList,
            upcomingBills = billList,
            possibleTransfers = detectedTransfers,
            overview = newOverview
        )
        _onboardingCompleted.value = (settingsMap["onboarding_completed"] ?: "false") == "true"
        runCatching { com.saannapunta.app.widget.SaanNaPuntaWidgetUpdater.requestUpdate(appContext) }
    }

    private fun computeOverview(
        accList: List<Account>,
        txList: List<TransactionItem>,
        budgetList: List<CategoryBudget>,
        cycleConfig: PayCycleConfig,
        goalsList: List<SavingsGoal> = _savingsGoals.value,
        upcomingBills: List<UpcomingBill> = _upcomingBills.value
    ) {
        _overview.value = buildOverview(accList, txList, budgetList, goalsList, upcomingBills, cycleConfig)
    }

    private fun buildOverview(
        accList: List<Account>,
        txList: List<TransactionItem>,
        budgetList: List<CategoryBudget>,
        goalsList: List<SavingsGoal>,
        upcomingBills: List<UpcomingBill>,
        cycleConfig: PayCycleConfig,
        nowMillis: Long = System.currentTimeMillis()
    ): MoneyOverview {
        val db = dbHelper.writableDatabase
        val storedDate = readSetting(db, "day_allowance_date")
        val storedAllowance = readSetting(db, "day_allowance_centavos")?.toLongOrNull()
        val snapshot = CycleCalculator.computeOverviewSnapshot(
            accList = accList,
            txList = txList,
            budgetList = budgetList,
            goalsList = goalsList,
            upcomingBills = upcomingBills,
            cycleConfig = cycleConfig,
            nowMillis = nowMillis,
            storedDayAllowanceDate = storedDate,
            storedDayAllowanceCentavos = storedAllowance
        )
        if ((storedDate != snapshot.dayAllowanceDate || storedAllowance == null) &&
            (accList.isNotEmpty() || budgetList.isNotEmpty() || readSetting(db, "onboarding_completed") == "true")
        ) {
            upsertSettingRow(db, "day_allowance_date", snapshot.dayAllowanceDate)
            upsertSettingRow(db, "day_allowance_centavos", snapshot.dayAllowanceCentavos.toString())
        }
        return snapshot
    }

    private fun upsertSettingRow(db: android.database.sqlite.SQLiteDatabase, key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict(
            SaanNaPuntaDatabaseHelper.TABLE_SETTINGS,
            null,
            cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Clears the cached daily allowance snapshot when the user modifies budgets, accounts, or pay cycle
     * settings so the new configuration immediately updates today's baseline allowance.
     */
    private fun clearDailyAllowanceSnapshot(db: android.database.sqlite.SQLiteDatabase) {
        db.delete(
            SaanNaPuntaDatabaseHelper.TABLE_SETTINGS,
            "key IN (?, ?)",
            arrayOf("day_allowance_date", "day_allowance_centavos")
        )
    }

    /**
     * Ensures that if today's `dayAllowance` has not yet been snapshotted after midnight, it is
     * snapshotted from current SQLite state BEFORE inserting a new transaction today.
     */
    private fun ensureTodayAllowanceSnapshot(
        db: android.database.sqlite.SQLiteDatabase,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val todayStr = CycleCalculator.toLocalDate(nowMillis).toString()
        val storedDate = readSetting(db, "day_allowance_date")
        val storedAllowance = readSetting(db, "day_allowance_centavos")?.toLongOrNull()
        if (storedDate == todayStr && storedAllowance != null) return
        if (readSetting(db, "onboarding_completed") != "true") return

        val activeAccs = readAccounts(db).filter { it.archivedAt == null }
        val txs = readTransactions(db)
        val budgets = db.query(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, null, null, null, null, null, "id ASC").use { c ->
            buildList { while (c.moveToNext()) add(cursorToBudget(c)) }
        }
        val goals = db.query(SaanNaPuntaDatabaseHelper.TABLE_GOALS, null, null, null, null, null, "id ASC").use { c ->
            buildList { while (c.moveToNext()) add(cursorToGoal(c)) }
        }
        val bills = db.query(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, null, "isPaid = 0", null, null, null, "dueDateTimestamp ASC").use { c ->
            buildList { while (c.moveToNext()) add(cursorToUpcomingBill(c)) }
        }
        val cycleTypeStr = readSetting(db, "pay_cycle_type") ?: PayCycleType.SEMI_MONTHLY_15_30.name
        val cycleType = runCatching { PayCycleType.valueOf(cycleTypeStr) }.getOrDefault(PayCycleType.SEMI_MONTHLY_15_30)
        val p1 = readSetting(db, "first_payday")?.toIntOrNull() ?: 15
        val p2 = readSetting(db, "second_payday")?.toIntOrNull() ?: 30
        val weekend = (readSetting(db, "weekend_rule") ?: "true") == "true"
        val cfg = PayCycleConfig(cycleType, p1, p2, weekend)

        val snap = CycleCalculator.computeOverviewSnapshot(
            accList = activeAccs,
            txList = txs,
            budgetList = budgets,
            goalsList = goals,
            upcomingBills = bills,
            cycleConfig = cfg,
            nowMillis = nowMillis,
            storedDayAllowanceDate = null,
            storedDayAllowanceCentavos = null
        )
        upsertSettingRow(db, "day_allowance_date", snap.dayAllowanceDate)
        upsertSettingRow(db, "day_allowance_centavos", snap.dayAllowanceCentavos.toString())
    }

    private inner class SqliteMutationStore(
        private val db: android.database.sqlite.SQLiteDatabase
    ) : MutationStore {

        override fun getAccountById(id: Long): Account? =
            db.query(
                SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS,
                null,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            ).use { c -> if (c.moveToFirst()) cursorToAccount(c) else null }

        override fun addAccountBalanceDelta(id: Long, storedDeltaCentavos: Long) {
            val updated = db.compileStatement(
                "UPDATE ${SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS} SET balanceCentavos = balanceCentavos + ? WHERE id = ?"
            ).use { stmt ->
                stmt.bindLong(1, storedDeltaCentavos)
                stmt.bindLong(2, id)
                stmt.executeUpdateDelete()
            }
            if (updated == 0) {
                throw IllegalArgumentException("Account with id=$id does not exist")
            }
        }

        override fun archiveAccount(id: Long, archivedAt: Long) {
            val cv = ContentValues().apply {
                put("archivedAt", archivedAt)
                put("isSpendable", 0)
                put("statusLine", "Archived account")
            }
            db.update(
                SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS,
                cv,
                "id = ?",
                arrayOf(id.toString())
            )
        }

        override fun getTransactionById(id: Long): TransactionItem? =
            db.query(
                SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS,
                null,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            ).use { c -> if (c.moveToFirst()) cursorToTransaction(c) else null }

        override fun insertTransaction(item: TransactionItem): Long =
            insertTransactionRow(db, item)

        override fun updateTransactionRow(item: TransactionItem) {
            val cv = ContentValues().apply {
                put("title", item.title)
                put("amountCentavos", item.amountCentavos)
                put("type", item.type.name)
                put("accountId", item.accountId)
                put("targetAccountId", item.targetAccountId)
                put("category", item.category)
                put("subCategory", item.subCategory)
                put("timestamp", item.timestamp)
                put("note", item.note)
                put("sourceInfo", item.sourceInfo)
                put("refNumber", item.refNumber)
                put("dedupKey", item.dedupKey)
            }
            db.update(
                SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS,
                cv,
                "id = ?",
                arrayOf(item.id.toString())
            )
        }

        override fun deleteTransactionRow(id: Long) {
            db.delete(
                SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS,
                "id = ?",
                arrayOf(id.toString())
            )
        }

        override fun getTransfersForAccount(accountId: Long): List<TransactionItem> =
            db.query(
                SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS,
                null,
                "type = ? AND (accountId = ? OR targetAccountId = ?)",
                arrayOf(TransactionType.TRANSFER.name, accountId.toString(), accountId.toString()),
                null,
                null,
                "id ASC"
            ).use { c ->
                buildList { while (c.moveToNext()) add(cursorToTransaction(c)) }
            }

        override fun getUpcomingBillById(id: Long): UpcomingBill? =
            db.query(
                SaanNaPuntaDatabaseHelper.TABLE_UPCOMING,
                null,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            ).use { c -> if (c.moveToFirst()) cursorToUpcomingBill(c) else null }

        override fun updateUpcomingBillState(id: Long, isPaid: Boolean, dueDateTimestamp: Long, anchorDayOfMonth: Int?) {
            val cv = ContentValues().apply {
                put("isPaid", if (isPaid) 1 else 0)
                put("dueDateTimestamp", dueDateTimestamp)
                if (anchorDayOfMonth != null) {
                    put("anchorDayOfMonth", anchorDayOfMonth)
                }
                putNull("lastRemindedFor")
            }
            db.update(
                SaanNaPuntaDatabaseHelper.TABLE_UPCOMING,
                cv,
                "id = ?",
                arrayOf(id.toString())
            )
        }

        override fun getSavingsGoalById(id: Long): SavingsGoal? =
            db.query(
                SaanNaPuntaDatabaseHelper.TABLE_GOALS,
                null,
                "id = ?",
                arrayOf(id.toString()),
                null,
                null,
                null
            ).use { c -> if (c.moveToFirst()) cursorToGoal(c) else null }

        override fun updateSavingsGoalRow(goal: SavingsGoal) {
            val cv = ContentValues().apply {
                put("name", goal.name)
                put("targetCentavos", goal.targetCentavos)
                put("savedCentavos", goal.savedCentavos)
                put("accountId", goal.accountId)
            }
            db.update(
                SaanNaPuntaDatabaseHelper.TABLE_GOALS,
                cv,
                "id = ?",
                arrayOf(goal.id.toString())
            )
        }

        override fun getBudgetCategories(): List<String> =
            db.query(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, arrayOf("category"), null, null, null, null, "id ASC").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
    }

    fun confirmPossibleTransfer(suggestion: PossibleTransferSuggestion) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var changed = false
            db.beginTransaction()
            try {
                changed = MutationEngine.confirmPossibleTransfer(
                    SqliteMutationStore(db),
                    suggestion.expenseTx.id,
                    suggestion.incomeTx.id
                )
                if (changed) {
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }
            if (changed) loadFromDb()
        }
    }

    fun dismissPossibleTransfer(suggestion: PossibleTransferSuggestion) {
        _possibleTransfers.value = _possibleTransfers.value.filterNot { it.pairKey == suggestion.pairKey }
        scope.launch {
            dismissedTransferPairs.add(suggestion.pairKey)
            upsertSettingRow(
                dbHelper.writableDatabase,
                "dismissed_transfer_pairs",
                JSONArray(dismissedTransferPairs.toList()).toString()
            )
        }
    }

    fun findSimilarAutoIngestedTransaction(
        accountId: Long,
        amountCentavos: Long,
        type: TransactionType,
        nowMillis: Long = System.currentTimeMillis()
    ): TransactionItem? =
        MutationEngine.findSimilarAutoIngestedTransaction(
            accountId = accountId,
            amountCentavos = amountCentavos,
            type = type,
            nowMillis = nowMillis,
            existingTransactions = _transactions.value
        )

    fun addTransaction(item: TransactionItem) {
        scope.launch {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                ensureTodayAllowanceSnapshot(db)
                insertTransactionRow(db, item)
                applyTransactionBalanceImpact(db, item, reverse = false)
                // Money coming in changes how much is safe today; spending must not, so only income resets it
                if (item.type == TransactionType.INCOME) clearDailyAllowanceSnapshot(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            loadFromDb()
        }
    }

    /**
     * Records a fast cash expense (from the widget bottom sheet or one-tap preset button).
     * Defaults to the user's `Cash` account (creating one if needed) and marks `sourceInfo = "Manual"`.
     */
    fun recordQuickCashExpense(
        amountCentavos: Long,
        category: String? = null,
        accountId: Long? = null,
        title: String? = null,
        note: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        onCreated: (Long, String, String) -> Unit = { _, _, _ -> }
    ) {
        if (amountCentavos <= 0L) return
        scope.launch {
            val db = dbHelper.writableDatabase
            var insertedId = -1L
            var resolvedCat = "Food & Dining"
            var resolvedAccName = "Cash on Hand"
            db.beginTransaction()
            try {
                ensureTodayAllowanceSnapshot(db, nowMillis)
                val accountsList = readAccounts(db)
                val existingTxs = readTransactions(db)
                val budgetsList = db.query(
                    SaanNaPuntaDatabaseHelper.TABLE_BUDGETS,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "id ASC"
                ).use { c -> buildList { while (c.moveToNext()) add(cursorToBudget(c)) } }

                val targetAcc = when {
                    accountId != null -> accountsList.firstOrNull { it.id == accountId && it.archivedAt == null }
                    else -> DailyHabitEngine.findCashAccount(accountsList)
                } ?: resolveTargetAccountForParsedTransfer(
                    db = db,
                    accounts = accountsList,
                    sourceAccountId = -1L,
                    parsed = com.saannapunta.app.parser.ParsedTransaction(
                        provider = "cash",
                        type = TransactionType.EXPENSE,
                        amountCentavos = amountCentavos,
                        title = "Cash",
                        category = "Uncategorized",
                        sourceInfo = "Manual",
                        rawMessage = ""
                    )
                )

                if (targetAcc != null) {
                    resolvedAccName = targetAcc.name
                    val recentCats = DailyHabitEngine.getRecentExpenseCategories(existingTxs, budgetsList, limit = 4)
                    resolvedCat = category?.trim()?.takeIf { it.isNotEmpty() }
                        ?: DailyHabitEngine.getMostFrequentCategoryForAmount(existingTxs, amountCentavos, recentCats)
                    val txTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: resolvedCat

                    val item = TransactionItem(
                        title = txTitle,
                        amountCentavos = amountCentavos,
                        type = TransactionType.EXPENSE,
                        accountId = targetAcc.id,
                        category = resolvedCat,
                        timestamp = nowMillis,
                        note = note?.trim()?.takeIf { it.isNotEmpty() },
                        sourceInfo = "Manual"
                    )
                    insertedId = insertTransactionRow(db, item)
                    applyTransactionBalanceImpact(db, item, reverse = false)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (insertedId > 0L) {
                loadFromDb()
                onCreated(insertedId, resolvedCat, resolvedAccName)
            }
        }
    }

    /**
     * Deletes a transaction by [txId] inside a DB transaction (used by the One-Tap Undo notification action).
     */
    fun deleteTransactionById(txId: Long, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var changed = false
            db.beginTransaction()
            try {
                changed = MutationEngine.deleteTransaction(SqliteMutationStore(db), txId)
                if (changed) {
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }
            if (changed) loadFromDb()
            onDone(changed)
        }
    }

    fun updateTransaction(oldItem: TransactionItem, newItem: TransactionItem) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var changed = false
            db.beginTransaction()
            try {
                changed = MutationEngine.updateTransaction(SqliteMutationStore(db), oldItem.id, newItem)
                if (changed) {
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }
            if (changed) loadFromDb()
        }
    }

    fun deleteTransaction(item: TransactionItem) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var changed = false
            db.beginTransaction()
            try {
                changed = MutationEngine.deleteTransaction(SqliteMutationStore(db), item.id)
                if (changed) {
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }
            if (changed) loadFromDb()
        }
    }

    /**
     * Moves money into (+) or out of (-) an account. Asset accounts store a balance, so money in
     * raises it. Liability accounts (credit cards, loans) store what is OWED, so money in (a payment)
     * lowers the outstanding amount and money out (a purchase) raises it.
     *
     * Throws [IllegalArgumentException] when [accountId] does not exist so the caller's transaction rolls back.
     */
    private fun adjustAccount(db: android.database.sqlite.SQLiteDatabase, accountId: Long, moneyIn: Long) {
        MutationEngine.adjustAccount(SqliteMutationStore(db), accountId, moneyIn)
    }

    private fun applyTransactionBalanceImpact(
        db: android.database.sqlite.SQLiteDatabase,
        item: TransactionItem,
        reverse: Boolean
    ) {
        MutationEngine.applyTransactionBalanceImpact(SqliteMutationStore(db), item, reverse)
    }

    private fun insertTransactionRow(db: android.database.sqlite.SQLiteDatabase, item: TransactionItem): Long {
        val cv = ContentValues().apply {
            put("title", item.title)
            put("amountCentavos", item.amountCentavos)
            put("type", item.type.name)
            put("accountId", item.accountId)
            put("targetAccountId", item.targetAccountId)
            put("category", item.category)
            put("subCategory", item.subCategory)
            put("timestamp", item.timestamp)
            put("note", item.note)
            put("sourceInfo", item.sourceInfo)
            put("refNumber", item.refNumber)
            put("dedupKey", item.dedupKey)
        }
        return db.insert(SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS, null, cv)
    }

    private fun readSetting(db: android.database.sqlite.SQLiteDatabase, key: String): String? =
        db.rawQuery(
            "SELECT value FROM ${SaanNaPuntaDatabaseHelper.TABLE_SETTINGS} WHERE key = ?",
            arrayOf(key)
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun readAccounts(db: android.database.sqlite.SQLiteDatabase): List<Account> =
        db.query(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, null, null, null, null, "id ASC").use { c ->
            buildList { while (c.moveToNext()) add(cursorToAccount(c)) }
        }

    private fun readTransactions(db: android.database.sqlite.SQLiteDatabase): List<TransactionItem> =
        db.query(SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS, null, null, null, null, null, "timestamp DESC").use { c ->
            buildList { while (c.moveToNext()) add(cursorToTransaction(c)) }
        }

    private fun logIngestEvent(
        db: android.database.sqlite.SQLiteDatabase,
        timestamp: Long,
        sender: String,
        rawMessage: String,
        status: String,
        reason: String
    ) {
        val cv = ContentValues().apply {
            put("timestamp", timestamp)
            put("sender", sender.take(120))
            put("bodyHash", SmsMessageParser.sha256Hex(rawMessage))
            put("status", status)
            put("reason", reason.take(500))
        }
        db.insert(SaanNaPuntaDatabaseHelper.TABLE_INGEST_LOG, null, cv)
    }

    private fun wasRecentlyRecorded(
        db: android.database.sqlite.SQLiteDatabase,
        sender: String,
        rawMessage: String,
        receivedAt: Long
    ): Boolean =
        db.rawQuery(
            "SELECT 1 FROM ${SaanNaPuntaDatabaseHelper.TABLE_INGEST_LOG} " +
                "WHERE status = 'RECORDED' AND sender = ? AND bodyHash = ? AND ABS(timestamp - ?) <= ? LIMIT 1",
            arrayOf(
                sender.take(120),
                SmsMessageParser.sha256Hex(rawMessage),
                receivedAt.toString(),
                DUPLICATE_BODY_WINDOW_MS.toString()
            )
        ).use { it.moveToFirst() }

    /** Creates an account for a parsed message (marked needsOpeningBalance = true until user confirms). */
    private fun createAccountForParsedMessage(
        db: android.database.sqlite.SQLiteDatabase,
        parsed: com.saannapunta.app.parser.ParsedTransaction
    ): Account? {
        val template = SmsMessageParser.buildAutoCreatedAccount(parsed) ?: return null
        val acv = ContentValues().apply {
            put("name", template.name)
            put("provider", template.provider)
            put("type", template.type.name)
            put("accountNumberMasked", template.accountNumberMasked)
            put("balanceCentavos", template.balanceCentavos)
            put("isSpendable", if (template.isSpendable) 1 else 0)
            put("statusLine", template.statusLine)
            put("iconResName", template.iconResName)
            put("balanceAsOf", template.balanceAsOf)
            put("needsOpeningBalance", if (template.needsOpeningBalance) 1 else 0)
            put("archivedAt", template.archivedAt)
        }
        val newId = db.insert(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, acv)
        return if (newId > 0) template.copy(id = newId) else null
    }

    /**
     * Records a bank/e-wallet SMS or push notification. Runs entirely on the serialized DB
     * dispatcher and reads state from SQLite (not the in-memory flows), so it is correct even when
     * the process was just started by the SMS broadcast and nothing has been loaded yet.
     *
     * Every recorded, ignored or failed message is logged to `ingest_log` with a SHA-256 body hash
     * (never the raw message body); recorded hashes are used to drop re-posted notifications.
     */
    fun parseAndRecordMessage(
        sender: String,
        messageText: String,
        receivedAt: Long = System.currentTimeMillis(),
        onDone: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var recorded = false
            try {
                when (val outcome = SmsMessageParser.parseDetailed(sender, messageText)) {
                    is com.saannapunta.app.parser.ParseOutcome.Ignored -> {
                        logIngestEvent(db, receivedAt, sender, messageText, "IGNORED", outcome.reason)
                    }
                    is com.saannapunta.app.parser.ParseOutcome.Success -> {
                        recorded = recordParsedMessage(db, sender, outcome.transaction, receivedAt)
                    }
                }
            } catch (t: Throwable) {
                try {
                    logIngestEvent(
                        db = db,
                        timestamp = receivedAt,
                        sender = sender,
                        rawMessage = messageText,
                        status = "FAILED",
                        reason = t.message ?: t.javaClass.simpleName
                    )
                } catch (_: Throwable) {
                }
            }
            if (recorded) loadFromDb()
            onDone(recorded)
        }
    }

    /**
     * Resolves or creates the user's CASH account for ATM withdrawals, or finds a matching
     * LIABILITY account for credit card bill payments.
     */
    private fun resolveTargetAccountForParsedTransfer(
        db: android.database.sqlite.SQLiteDatabase,
        accounts: List<Account>,
        sourceAccountId: Long,
        parsed: com.saannapunta.app.parser.ParsedTransaction
    ): Account? {
        if (parsed.category.equals("Credit Card Payment", ignoreCase = true)) {
            val activeLiabilities = accounts.filter { it.archivedAt == null && it.type == AccountType.LIABILITY }
            val titleUpper = parsed.title.uppercase()
            return activeLiabilities.firstOrNull {
                titleUpper.contains(it.provider.uppercase()) || titleUpper.contains(it.name.uppercase())
            } ?: activeLiabilities.firstOrNull()
        }

        // ATM withdrawal -> user's CASH account (create one if none exists)
        val existingCash = accounts.firstOrNull {
            it.archivedAt == null &&
                it.id != sourceAccountId &&
                (it.type == AccountType.CASH || it.provider.equals("cash", ignoreCase = true))
        }
        if (existingCash != null) return existingCash

        val cashInfo = ProviderCatalog.all.firstOrNull { it.provider == "cash" }
        val cashTemplate = Account(
            id = 0L,
            name = cashInfo?.name ?: "Cash on Hand",
            provider = "cash",
            type = AccountType.CASH,
            accountNumberMasked = "",
            balanceCentavos = 0L,
            isSpendable = true,
            statusLine = "Cash wallet",
            iconResName = cashInfo?.iconResName ?: "ic_cash",
            balanceAsOf = 0L,
            needsOpeningBalance = false,
            archivedAt = null
        )
        val cv = ContentValues().apply {
            put("name", cashTemplate.name)
            put("provider", cashTemplate.provider)
            put("type", cashTemplate.type.name)
            put("accountNumberMasked", cashTemplate.accountNumberMasked)
            put("balanceCentavos", cashTemplate.balanceCentavos)
            put("isSpendable", 1)
            put("statusLine", cashTemplate.statusLine)
            put("iconResName", cashTemplate.iconResName)
            put("balanceAsOf", 0L)
            put("needsOpeningBalance", 0)
            putNull("archivedAt")
        }
        val newId = db.insert(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, cv)
        return if (newId > 0) cashTemplate.copy(id = newId) else null
    }

    private fun recordParsedMessage(
        db: android.database.sqlite.SQLiteDatabase,
        sender: String,
        parsed: com.saannapunta.app.parser.ParsedTransaction,
        receivedAt: Long
    ): Boolean {
        if (readSetting(db, "onboarding_completed") != "true") {
            logIngestEvent(db, receivedAt, sender, parsed.rawMessage, "IGNORED", "Onboarding not completed")
            return false
        }

        db.beginTransaction()
        try {
            ensureTodayAllowanceSnapshot(db)
            val accounts = readAccounts(db)
            val existingTxs = readTransactions(db)
            val matchedAccount = SmsMessageParser.matchAccount(accounts, parsed)

            if (SmsMessageParser.isDuplicateTransaction(parsed, matchedAccount?.id, receivedAt, existingTxs)) {
                logIngestEvent(db, receivedAt, sender, parsed.rawMessage, "IGNORED", "Duplicate transaction")
                db.setTransactionSuccessful()
                return false
            }
            // Apps re-post (update) the same notification; an identical body from the same sender
            // that was already recorded recently is the same transaction, not a new one.
            if (wasRecentlyRecorded(db, sender, parsed.rawMessage, receivedAt)) {
                logIngestEvent(db, receivedAt, sender, parsed.rawMessage, "IGNORED", "Duplicate message body")
                db.setTransactionSuccessful()
                return false
            }

            val account = matchedAccount ?: createAccountForParsedMessage(db, parsed)
            if (account == null) {
                logIngestEvent(
                    db = db,
                    timestamp = receivedAt,
                    sender = sender,
                    rawMessage = parsed.rawMessage,
                    status = "FAILED",
                    reason = "Could not resolve or create account for provider ${parsed.provider}"
                )
                db.setTransactionSuccessful()
                return false
            }

            val targetAccount = if (parsed.type == TransactionType.TRANSFER) {
                resolveTargetAccountForParsedTransfer(db, accounts, account.id, parsed)
            } else null

            val item = TransactionItem(
                title = parsed.title,
                amountCentavos = parsed.amountCentavos,
                type = parsed.type,
                accountId = account.id,
                targetAccountId = targetAccount?.id,
                category = parsed.category,
                timestamp = receivedAt,
                note = null,
                sourceInfo = parsed.sourceInfo,
                refNumber = parsed.refNumber,
                dedupKey = parsed.dedupKey
            )
            insertTransactionRow(db, item)
            if (item.type == TransactionType.INCOME) clearDailyAllowanceSnapshot(db)

            val syncOutcome = SmsMessageParser.computeBalanceSyncOutcome(account, parsed, receivedAt)
            if (syncOutcome.applyTxDelta) {
                applyTransactionBalanceImpact(db, item, reverse = false)
            } else if (targetAccount != null) {
                adjustAccount(db, targetAccount.id, parsed.amountCentavos)
            }
            if (syncOutcome.adjustmentTransaction != null) {
                insertTransactionRow(db, syncOutcome.adjustmentTransaction)
                applyTransactionBalanceImpact(db, syncOutcome.adjustmentTransaction, reverse = false)
            }

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(receivedAt))
            val newStatus = when {
                account.needsOpeningBalance -> "Needs opening balance"
                parsed.newBalanceCentavos != null && !parsed.isCreditCard && account.type != AccountType.LIABILITY && syncOutcome.newBalanceAsOf == receivedAt ->
                    "Synced from message $timeStr"
                else -> "Last message read $timeStr"
            }
            db.execSQL(
                "UPDATE ${SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS} SET balanceAsOf = ?, statusLine = ? WHERE id = ?",
                arrayOf<Any>(syncOutcome.newBalanceAsOf, newStatus, account.id)
            )
            logIngestEvent(db, receivedAt, sender, parsed.rawMessage, "RECORDED", "Recorded")

            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun addAccount(account: Account) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("name", account.name)
                put("provider", account.provider)
                put("type", account.type.name)
                put("accountNumberMasked", account.accountNumberMasked)
                put("balanceCentavos", account.balanceCentavos)
                put("isSpendable", if (account.isSpendable) 1 else 0)
                put("statusLine", account.statusLine)
                put("iconResName", account.iconResName)
                put("balanceAsOf", if (account.balanceAsOf > 0L) account.balanceAsOf else System.currentTimeMillis())
                put("needsOpeningBalance", if (account.needsOpeningBalance) 1 else 0)
                put("archivedAt", account.archivedAt)
            }
            db.insert(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, cv)
            clearDailyAllowanceSnapshot(db)
            loadFromDb()
        }
    }

    fun updateAccount(account: Account) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("name", account.name)
                put("provider", account.provider)
                put("type", account.type.name)
                put("accountNumberMasked", account.accountNumberMasked)
                put("balanceCentavos", account.balanceCentavos)
                put("isSpendable", if (account.isSpendable) 1 else 0)
                put("statusLine", if (account.statusLine == "Needs opening balance") "Manual account" else account.statusLine)
                put("iconResName", account.iconResName)
                put("balanceAsOf", max(account.balanceAsOf, System.currentTimeMillis()))
                put("needsOpeningBalance", 0)
                put("archivedAt", account.archivedAt)
            }
            db.update(
                SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS,
                cv,
                "id = ?",
                arrayOf(account.id.toString())
            )
            clearDailyAllowanceSnapshot(db)
            loadFromDb()
        }
    }

    fun deleteAccount(accountId: Long) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var changed = false
            db.beginTransaction()
            try {
                changed = MutationEngine.deleteAccount(SqliteMutationStore(db), accountId)
                if (changed) {
                    clearDailyAllowanceSnapshot(db)
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }
            if (changed) loadFromDb()
        }
    }

    fun addCategoryBudget(budget: CategoryBudget) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("category", budget.category)
                put("limitCentavos", budget.limitCentavos)
                put("examples", budget.examples)
                put("iconName", budget.iconName)
                put("colorHex", budget.colorHex)
                put("subCategories", if (budget.subCategories.isNotEmpty()) JSONArray(budget.subCategories).toString() else null)
            }
            if (budget.id > 0) {
                db.update(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, cv, "id = ?", arrayOf(budget.id.toString()))
            } else {
                db.insertWithOnConflict(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            clearDailyAllowanceSnapshot(db)
            loadFromDb()
        }
    }

    fun deleteCategoryBudget(budgetId: Long) {
        scope.launch {
            val db = dbHelper.writableDatabase
            db.delete(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, "id = ?", arrayOf(budgetId.toString()))
            clearDailyAllowanceSnapshot(db)
            loadFromDb()
        }
    }

    fun saveSavingsGoal(goal: SavingsGoal) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("name", goal.name)
                put("targetCentavos", goal.targetCentavos)
                put("savedCentavos", goal.savedCentavos)
                put("accountId", goal.accountId)
            }
            if (goal.id > 0) {
                db.update(SaanNaPuntaDatabaseHelper.TABLE_GOALS, cv, "id = ?", arrayOf(goal.id.toString()))
            } else {
                db.insert(SaanNaPuntaDatabaseHelper.TABLE_GOALS, null, cv)
            }
            loadFromDb()
        }
    }

    fun deleteSavingsGoal(goalId: Long) {
        scope.launch {
            val db = dbHelper.writableDatabase
            db.delete(SaanNaPuntaDatabaseHelper.TABLE_GOALS, "id = ?", arrayOf(goalId.toString()))
            loadFromDb()
        }
    }

    /**
     * Adds [amountCentavos] to [goal] as an earmark on [fromAccountId]. Reduces spendable balance
     * without reducing net worth, and never logs an EXPENSE transaction toward the Personal budget.
     */
    fun contributeToSavingsGoal(goal: SavingsGoal, amountCentavos: Long, fromAccountId: Long?) {
        if (amountCentavos <= 0L) return
        scope.launch {
            val db = dbHelper.writableDatabase
            var changed = false
            db.beginTransaction()
            try {
                changed = MutationEngine.contributeToSavingsGoal(
                    SqliteMutationStore(db),
                    goal.id,
                    amountCentavos,
                    fromAccountId
                )
                if (changed) {
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }
            if (changed) loadFromDb()
        }
    }

    /**
     * Clears all transactions and resets account balances and goal progress to zero, while preserving
     * configured accounts, category budgets, pay cycle, and profile settings.
     */
    fun startFreshPreserveSetup() {
        scope.launch {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS, null, null)
                db.execSQL("UPDATE ${SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS} SET balanceCentavos = 0, statusLine = 'Reset to ₱0'")
                db.execSQL("UPDATE ${SaanNaPuntaDatabaseHelper.TABLE_GOALS} SET savedCentavos = 0")
                clearDailyAllowanceSnapshot(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            loadFromDb()
        }
    }

    /** Permanently deletes every row and returns the app to first-run onboarding. */
    fun eraseAllData() {
        scope.launch {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_GOALS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_SETTINGS, null, null)
                dbHelper.writeDefaultSettings(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            loadFromDb()
        }
    }


    // ---------- Upcoming bills ----------

    fun saveUpcomingBill(bill: UpcomingBill) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val anchorDay = bill.anchorDayOfMonth ?: CycleCalculator.toLocalDate(bill.dueDateTimestamp).dayOfMonth
            val cv = ContentValues().apply {
                put("title", bill.title)
                put("amountCentavos", bill.amountCentavos)
                put("dueDateTimestamp", bill.dueDateTimestamp)
                put("isPaid", if (bill.isPaid) 1 else 0)
                put("recurring", if (bill.recurring) 1 else 0)
                put("category", bill.category)
                put("subCategory", bill.subCategory)
                put("anchorDayOfMonth", anchorDay)
                if (bill.lastRemindedFor != null) {
                    put("lastRemindedFor", bill.lastRemindedFor)
                } else {
                    putNull("lastRemindedFor")
                }
            }
            if (bill.id > 0) {
                db.update(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, cv, "id = ?", arrayOf(bill.id.toString()))
            } else {
                db.insert(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, null, cv)
            }
            clearDailyAllowanceSnapshot(db)
            loadFromDb()
        }
    }

    /**
     * Reads unpaid bills and the reminder lead time straight from SQLite. The reminder worker
     * usually runs in a freshly started process where the in-memory flows are still empty.
     */
    suspend fun loadUpcomingBillsForReminders(): Pair<List<UpcomingBill>, Int> =
        kotlinx.coroutines.withContext(dbDispatcher) {
            val db = dbHelper.readableDatabase
            val bills = db.query(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, null, "isPaid = 0", null, null, null, "dueDateTimestamp ASC").use { c ->
                buildList { while (c.moveToNext()) add(cursorToUpcomingBill(c)) }
            }
            val daysBefore = (readSetting(db, "bill_reminder_days_before")?.toIntOrNull() ?: 3).coerceIn(0, 30)
            Pair(bills, daysBefore)
        }

    fun updateBillLastReminded(billId: Long, lastRemindedFor: String) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("lastRemindedFor", lastRemindedFor)
            }
            db.update(
                SaanNaPuntaDatabaseHelper.TABLE_UPCOMING,
                cv,
                "id = ?",
                arrayOf(billId.toString())
            )
            loadFromDb()
        }
    }

    fun deleteUpcomingBill(billId: Long) {
        scope.launch {
            val db = dbHelper.writableDatabase
            db.delete(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, "id = ?", arrayOf(billId.toString()))
            clearDailyAllowanceSnapshot(db)
            loadFromDb()
        }
    }

    /**
     * Logs the bill as a Utilities expense from [accountId]. Recurring bills move to the same day next
     * month; one-off bills are marked paid and leave the Upcoming list.
     *
     * Reads the current bill row from SQLite inside the transaction so a double tap is a no-op.
     */
    fun markBillPaid(bill: UpcomingBill, accountId: Long) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var changed = false
            db.beginTransaction()
            try {
                ensureTodayAllowanceSnapshot(db)
                changed = MutationEngine.markBillPaid(SqliteMutationStore(db), bill, accountId)
                if (changed) {
                    db.setTransactionSuccessful()
                }
            } finally {
                db.endTransaction()
            }
            if (changed) loadFromDb()
        }
    }

    // ---------- Onboarding ----------

    /** Writes everything the user chose in onboarding, replacing any existing data. */
    fun completeOnboarding(setup: OnboardingSetup) {
        scope.launch {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_GOALS, null, null)
                db.delete(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, null, null)

                val settings = mapOf(
                    "onboarding_completed" to "true",
                    "user_name" to setup.name.trim(),
                    "pay_cycle_type" to setup.cycleType.name,
                    "first_payday" to setup.firstPayday.toString(),
                    "second_payday" to setup.secondPayday.toString(),
                    "weekend_rule" to setup.weekendRule.toString()
                )
                for ((k, v) in settings) {
                    val cv = ContentValues().apply { put("key", k); put("value", v) }
                    db.insertWithOnConflict(SaanNaPuntaDatabaseHelper.TABLE_SETTINGS, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                }

                val nowMillis = System.currentTimeMillis()
                for ((info, openingBalance) in setup.accounts) {
                    val cv = ContentValues().apply {
                        put("name", info.name)
                        put("provider", info.provider)
                        put("type", info.type.name)
                        put("accountNumberMasked", "")
                        put("balanceCentavos", openingBalance)
                        put("isSpendable", if (info.type == AccountType.LIABILITY) 0 else 1)
                        put("statusLine", "Manual account")
                        put("iconResName", info.iconResName)
                        put("balanceAsOf", nowMillis)
                        put("needsOpeningBalance", 0)
                        putNull("archivedAt")
                    }
                    db.insert(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, cv)
                }

                for (b in setup.budgets) {
                    val cv = ContentValues().apply {
                        put("category", b.category)
                        put("limitCentavos", b.limitCentavos)
                        put("examples", b.examples)
                        put("iconName", b.iconName)
                        put("colorHex", b.colorHex)
                        put("subCategories", if (b.subCategories.isNotEmpty()) JSONArray(b.subCategories).toString() else null)
                    }
                    db.insertWithOnConflict(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                }

                for (g in setup.goals) {
                    val cv = ContentValues().apply {
                        put("name", g.name)
                        put("targetCentavos", g.targetCentavos)
                        put("savedCentavos", g.savedCentavos)
                    }
                    db.insert(SaanNaPuntaDatabaseHelper.TABLE_GOALS, null, cv)
                }
                clearDailyAllowanceSnapshot(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            loadFromDb()
        }
    }

    /** Next payday for a not-yet-saved config, used by the onboarding preview. */
    fun previewNextPayday(config: PayCycleConfig): String {
        return CycleCalculator.previewNextPayday(config)
    }

    /** Days left in the cycle (including today) for a not-yet-saved config. */
    fun previewDaysLeft(config: PayCycleConfig): Int {
        return CycleCalculator.previewDaysLeft(config)
    }

    fun updatePayCycleSettings(
        firstDay: Int,
        secondDay: Int,
        weekendRule: Boolean,
        cycleType: PayCycleType = _payCycleConfig.value.type
    ) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val entries = mapOf(
                "first_payday" to firstDay.toString(),
                "second_payday" to secondDay.toString(),
                "weekend_rule" to weekendRule.toString(),
                "pay_cycle_type" to cycleType.name
            )
            for ((k, v) in entries) {
                val cv = ContentValues().apply { put("key", k); put("value", v) }
                db.insertWithOnConflict(SaanNaPuntaDatabaseHelper.TABLE_SETTINGS, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            clearDailyAllowanceSnapshot(db)
            loadFromDb()
        }
    }

    fun updateUserName(newName: String) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("key", "user_name")
                put("value", newName.trim())
            }
            db.insertWithOnConflict(SaanNaPuntaDatabaseHelper.TABLE_SETTINGS, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            loadFromDb()
        }
    }

    /**
     * Full-fidelity backup in Version 3 format: exports stable account IDs and references accounts
     * by ID (`accountId`, `targetAccountId`) while retaining `account` / `targetAccount` names.
     * Does NOT set `last_backup_date` — call [recordBackupFileWritten] only after the file write succeeds.
     */
    fun exportBackupJson(): String {
        val accs = _allAccounts.value.ifEmpty { _accounts.value }
        return BackupAndCsvCodec.exportBackupJsonV3(
            accounts = accs,
            transactions = _transactions.value,
            budgets = _budgets.value,
            goals = _savingsGoals.value,
            bills = _upcomingBills.value,
            payCycleConfig = _payCycleConfig.value,
            userName = _userName.value
        )
    }

    /**
     * Updates `last_backup_date` only after the backup file has been successfully written to disk.
     */
    fun recordBackupFileWritten(timestampMillis: Long = System.currentTimeMillis()) {
        val nowStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestampMillis))
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("key", "last_backup_date")
                put("value", nowStr)
            }
            db.insertWithOnConflict(
                SaanNaPuntaDatabaseHelper.TABLE_SETTINGS,
                null,
                cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
            _lastBackupDate.value = nowStr
        }
    }

    /**
     * Validates and restores a Version 3 or Version 2/1 JSON backup on the serialized DB writer,
     * suspending until the restore transaction completes and returning the real result.
     * Orphaned transactions (missing account reference) are never silently reassigned to the first
     * account; they are skipped and reported in [BackupImportResult].
     */
    suspend fun importBackupJsonDetailed(jsonString: String): BackupImportResult {
        val (validationResult, restoreData) = kotlinx.coroutines.withContext(Dispatchers.Default) {
            BackupAndCsvCodec.validateAndResolveBackupJson(jsonString)
        }
        if (!validationResult.success || restoreData == null) {
            return validationResult
        }

        return kotlinx.coroutines.withContext(dbDispatcher) {
            try {
                val db = dbHelper.writableDatabase
                db.beginTransaction()
                try {
                    // Delete child tables before parent accounts table for foreign-key safety
                    db.delete(SaanNaPuntaDatabaseHelper.TABLE_TRANSACTIONS, null, null)
                    if (restoreData.goals != null) {
                        db.delete(SaanNaPuntaDatabaseHelper.TABLE_GOALS, null, null)
                    }
                    db.delete(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, null)

                    for (a in restoreData.accounts) {
                        val cv = ContentValues().apply {
                            put("id", a.id)
                            put("name", a.name)
                            put("provider", a.provider)
                            put("type", a.type.name)
                            put("accountNumberMasked", a.accountNumberMasked)
                            put("balanceCentavos", a.balanceCentavos)
                            put("isSpendable", if (a.isSpendable) 1 else 0)
                            put("statusLine", a.statusLine)
                            put("iconResName", a.iconResName)
                            put("balanceAsOf", a.balanceAsOf)
                            put("needsOpeningBalance", if (a.needsOpeningBalance) 1 else 0)
                            if (a.archivedAt != null) put("archivedAt", a.archivedAt) else putNull("archivedAt")
                        }
                        db.insert(SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS, null, cv)
                    }

                    for (tx in restoreData.transactions) {
                        insertTransactionRow(db, tx)
                    }

                    restoreData.budgets?.let { budgets ->
                        db.delete(SaanNaPuntaDatabaseHelper.TABLE_BUDGETS, null, null)
                        for (b in budgets) {
                            db.insertWithOnConflict(
                                SaanNaPuntaDatabaseHelper.TABLE_BUDGETS,
                                null,
                                ContentValues().apply {
                                    put("category", b.category)
                                    put("limitCentavos", b.limitCentavos)
                                    put("examples", b.examples)
                                    put("iconName", b.iconName)
                                    put("colorHex", b.colorHex)
                                    put(
                                        "subCategories",
                                        if (b.subCategories.isNotEmpty()) JSONArray(b.subCategories).toString() else null
                                    )
                                },
                                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                            )
                        }
                    }

                    restoreData.goals?.let { goals ->
                        for (g in goals) {
                            db.insert(
                                SaanNaPuntaDatabaseHelper.TABLE_GOALS,
                                null,
                                ContentValues().apply {
                                    put("name", g.name)
                                    put("targetCentavos", g.targetCentavos)
                                    put("savedCentavos", g.savedCentavos)
                                    if (g.accountId != null) put("accountId", g.accountId) else putNull("accountId")
                                }
                            )
                        }
                    }

                    restoreData.bills?.let { bills ->
                        db.delete(SaanNaPuntaDatabaseHelper.TABLE_UPCOMING, null, null)
                        for (b in bills) {
                            db.insert(
                                SaanNaPuntaDatabaseHelper.TABLE_UPCOMING,
                                null,
                                ContentValues().apply {
                                    put("title", b.title)
                                    put("amountCentavos", b.amountCentavos)
                                    put("dueDateTimestamp", b.dueDateTimestamp)
                                    put("isPaid", 0)
                                    put("recurring", if (b.recurring) 1 else 0)
                                    put("category", b.category)
                                    put("subCategory", b.subCategory)
                                    put("anchorDayOfMonth", b.anchorDayOfMonth)
                                    put("lastRemindedFor", b.lastRemindedFor)
                                }
                            )
                        }
                    }

                    clearDailyAllowanceSnapshot(db)

                    restoreData.settings?.let { settings ->
                        for ((k, v) in settings) {
                            db.insertWithOnConflict(
                                SaanNaPuntaDatabaseHelper.TABLE_SETTINGS,
                                null,
                                ContentValues().apply {
                                    put("key", k)
                                    put("value", v)
                                },
                                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                            )
                        }
                    }

                    db.insertWithOnConflict(
                        SaanNaPuntaDatabaseHelper.TABLE_SETTINGS,
                        null,
                        ContentValues().apply {
                            put("key", "onboarding_completed")
                            put("value", "true")
                        },
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                    )

                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                loadFromDb()
                if (validationResult.skippedOrphanedTransactions > 0) {
                    _lastError.value = "Skipped ${validationResult.skippedOrphanedTransactions} orphaned transaction(s) with missing accounts"
                }
                validationResult
            } catch (t: Throwable) {
                val msg = t.message ?: t.javaClass.simpleName
                _lastError.value = msg
                BackupImportResult(
                    success = false,
                    version = validationResult.version,
                    errorMessage = msg
                )
            }
        }
    }

    private fun cursorToAccount(c: Cursor): Account {
        val balanceAsOfIdx = c.getColumnIndex("balanceAsOf")
        val needsOpeningIdx = c.getColumnIndex("needsOpeningBalance")
        val archivedAtIdx = c.getColumnIndex("archivedAt")
        return Account(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            provider = c.getString(c.getColumnIndexOrThrow("provider")),
            type = AccountType.valueOf(c.getString(c.getColumnIndexOrThrow("type"))),
            accountNumberMasked = c.getString(c.getColumnIndexOrThrow("accountNumberMasked")) ?: "",
            balanceCentavos = c.getLong(c.getColumnIndexOrThrow("balanceCentavos")),
            isSpendable = c.getInt(c.getColumnIndexOrThrow("isSpendable")) == 1,
            statusLine = c.getString(c.getColumnIndexOrThrow("statusLine")) ?: "Manual account",
            iconResName = c.getString(c.getColumnIndexOrThrow("iconResName")),
            balanceAsOf = if (balanceAsOfIdx != -1 && !c.isNull(balanceAsOfIdx)) c.getLong(balanceAsOfIdx) else 0L,
            needsOpeningBalance = if (needsOpeningIdx != -1 && !c.isNull(needsOpeningIdx)) c.getInt(needsOpeningIdx) == 1 else false,
            archivedAt = if (archivedAtIdx != -1 && !c.isNull(archivedAtIdx)) c.getLong(archivedAtIdx) else null
        )
    }

    private fun cursorToTransaction(c: Cursor): TransactionItem {
        val targetIdx = c.getColumnIndexOrThrow("targetAccountId")
        val targetId = if (c.isNull(targetIdx)) null else c.getLong(targetIdx)
        val refIdx = c.getColumnIndex("refNumber")
        val dedupIdx = c.getColumnIndex("dedupKey")
        return TransactionItem(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            amountCentavos = c.getLong(c.getColumnIndexOrThrow("amountCentavos")),
            type = TransactionType.valueOf(c.getString(c.getColumnIndexOrThrow("type"))),
            accountId = c.getLong(c.getColumnIndexOrThrow("accountId")),
            targetAccountId = targetId,
            category = c.getString(c.getColumnIndexOrThrow("category")),
            subCategory = c.getColumnIndex("subCategory").let { if (it != -1 && !c.isNull(it)) c.getString(it) else null },
            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
            note = c.getString(c.getColumnIndexOrThrow("note")),
            sourceInfo = c.getString(c.getColumnIndexOrThrow("sourceInfo")) ?: "Manual",
            refNumber = if (refIdx != -1 && !c.isNull(refIdx)) c.getString(refIdx) else null,
            dedupKey = if (dedupIdx != -1 && !c.isNull(dedupIdx)) c.getString(dedupIdx) else null
        )
    }

    private fun cursorToBudget(c: Cursor): CategoryBudget {
        val iconIdx = c.getColumnIndex("iconName")
        val colorIdx = c.getColumnIndex("colorHex")
        val subCatsIdx = c.getColumnIndex("subCategories")
        val subCatsList = if (subCatsIdx != -1 && !c.isNull(subCatsIdx)) {
            val raw = c.getString(subCatsIdx)
            try {
                val arr = org.json.JSONArray(raw)
                List(arr.length()) { i -> arr.getString(i) }
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        return CategoryBudget(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            category = c.getString(c.getColumnIndexOrThrow("category")),
            limitCentavos = c.getLong(c.getColumnIndexOrThrow("limitCentavos")),
            examples = c.getString(c.getColumnIndexOrThrow("examples")) ?: "",
            iconName = if (iconIdx != -1 && !c.isNull(iconIdx)) c.getString(iconIdx) else null,
            colorHex = if (colorIdx != -1 && !c.isNull(colorIdx)) c.getString(colorIdx) else null,
            subCategories = subCatsList
        )
    }

    private fun cursorToGoal(c: Cursor): SavingsGoal {
        val accIdx = c.getColumnIndex("accountId")
        return SavingsGoal(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            targetCentavos = c.getLong(c.getColumnIndexOrThrow("targetCentavos")),
            savedCentavos = c.getLong(c.getColumnIndexOrThrow("savedCentavos")),
            accountId = if (accIdx != -1 && !c.isNull(accIdx)) c.getLong(accIdx) else null
        )
    }

    private fun cursorToUpcomingBill(c: Cursor): UpcomingBill {
        val recIdx = c.getColumnIndex("recurring")
        val isRecurring = if (recIdx != -1) c.getInt(recIdx) == 1 else true
        val catIdx = c.getColumnIndex("category")
        val subCatIdx = c.getColumnIndex("subCategory")
        val anchorIdx = c.getColumnIndex("anchorDayOfMonth")
        val lastRemindedIdx = c.getColumnIndex("lastRemindedFor")
        val category = if (catIdx != -1 && !c.isNull(catIdx)) c.getString(catIdx) else "Utilities"
        val subCategory = if (subCatIdx != -1 && !c.isNull(subCatIdx)) c.getString(subCatIdx) else null
        val anchorDay = if (anchorIdx != -1 && !c.isNull(anchorIdx)) c.getInt(anchorIdx) else null
        val lastRemindedFor = if (lastRemindedIdx != -1 && !c.isNull(lastRemindedIdx)) c.getString(lastRemindedIdx) else null

        return UpcomingBill(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            amountCentavos = c.getLong(c.getColumnIndexOrThrow("amountCentavos")),
            dueDateTimestamp = c.getLong(c.getColumnIndexOrThrow("dueDateTimestamp")),
            isPaid = c.getInt(c.getColumnIndexOrThrow("isPaid")) == 1,
            recurring = isRecurring,
            category = category,
            subCategory = subCategory,
            anchorDayOfMonth = anchorDay,
            lastRemindedFor = lastRemindedFor
        )
    }

    fun getSubCategoriesForCategory(categoryName: String): List<String> {
        val existing = _budgets.value.firstOrNull { it.category.equals(categoryName, ignoreCase = true) }
        if (existing != null && existing.subCategories.isNotEmpty()) {
            return existing.subCategories
        }
        val lower = categoryName.lowercase()
        return when {
            lower.contains("util") || lower.contains("bill") ->
                listOf("Kuryente", "Tubig", "Internet", "Telepono / Load", "Gas / LPG", "Condo / Assoc Dues")
            lower.contains("food") || lower.contains("din") ->
                listOf("Groceries", "Fast Food", "Coffee & Milk Tea", "Restaurants", "Karinderya & Street Food", "Snacks")
            lower.contains("trans") || lower.contains("commute") ->
                listOf("Commute / Jeep / Bus", "Grab / Angkas / Taxi", "Gas / Fuel", "Toll / RFID", "Parking", "Maintenance")
            lower.contains("shop") || lower.contains("personal") ->
                listOf("Clothes & Apparel", "Skincare & Toiletries", "Online Shopping", "Haircut / Salon", "Gadgets")
            lower.contains("rent") || lower.contains("hous") ->
                listOf("House Rent", "Condo Rent", "Bedspace", "Repairs")
            lower.contains("health") || lower.contains("med") ->
                listOf("Medicine / Pharmacy", "Doctor / Checkup", "Dentist", "Vitamins & Supplements", "Hospital")
            lower.contains("edu") || lower.contains("school") ->
                listOf("Tuition", "School Supplies", "Books", "Allowance / Baon", "Uniform")
            lower.contains("entertain") || lower.contains("subscript") ->
                listOf("Streaming (Netflix)", "Gaming / Steam", "Movies / Cinema", "Outing & Vacation", "Concerts")
            lower.contains("fam") || lower.contains("pet") ->
                listOf("Pet Food / Vet", "Family Allowance", "Baby Care", "Gifts / Padala")
            lower.contains("sal") || lower.contains("income") || lower.contains("sahod") ->
                listOf("Payroll", "Side Hustle / Raket", "Allowance / Baon", "Business Sales", "Bonus / 13th Month")
            else -> emptyList()
        }
    }
    fun setBiometricLockEnabled(enabled: Boolean) {
        scope.launch {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("key", "biometric_lock_enabled")
                put("value", if (enabled) "true" else "false")
            }
            db.insertWithOnConflict(
                SaanNaPuntaDatabaseHelper.TABLE_SETTINGS,
                null,
                cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
            _biometricLockEnabled.value = enabled
            runCatching { com.saannapunta.app.widget.SaanNaPuntaWidgetUpdater.requestUpdate(appContext) }
        }
    }

    fun setWidgetHideAmounts(hide: Boolean) {
        scope.launch {
            val db = dbHelper.writableDatabase
            upsertSettingRow(db, "widget_hide_amounts", if (hide) "true" else "false")
            _widgetHideAmounts.value = hide
            runCatching { com.saannapunta.app.widget.SaanNaPuntaWidgetUpdater.requestUpdate(appContext) }
        }
    }

    fun setWidgetOneTapMode(enabled: Boolean) {
        scope.launch {
            val db = dbHelper.writableDatabase
            upsertSettingRow(db, "widget_one_tap_mode", if (enabled) "true" else "false")
            _widgetOneTapMode.value = enabled
            runCatching { com.saannapunta.app.widget.SaanNaPuntaWidgetUpdater.requestUpdate(appContext) }
        }
    }

    fun setUiSoundsEnabled(enabled: Boolean) {
        _uiSoundsEnabled.value = enabled
        scope.launch {
            upsertSettingRow(dbHelper.writableDatabase, "ui_sounds_enabled", if (enabled) "true" else "false")
        }
    }

    fun setBillReminderDaysBefore(days: Int) {
        val coerced = days.coerceIn(0, 30)
        scope.launch {
            val db = dbHelper.writableDatabase
            upsertSettingRow(db, "bill_reminder_days_before", coerced.toString())
            _billReminderDaysBefore.value = coerced
            runCatching { com.saannapunta.app.reminders.BillReminderScheduler.scheduleDailyCheck(appContext) }
        }
    }

    fun exportTransactionsCsv(includeRawSmsNotes: Boolean = false): String {
        val accs = _allAccounts.value.ifEmpty { _accounts.value }
        return BackupAndCsvCodec.exportTransactionsCsv(
            transactions = _transactions.value,
            accounts = accs,
            includeRawSmsNotes = includeRawSmsNotes
        )
    }

    /**
     * Imports reviewed SMS candidates. Each message is matched by last-4 / provider (never booking
     * a credit card message to a savings account), re-checked for duplicates inside the DB
     * transaction, and synced using balanceAsOf + Adjustment transactions when a newer reported
     * balance differs from the running total.
     */
    fun importBackfillTransactions(
        candidates: List<com.saannapunta.app.parser.SmsBackfillCandidate>,
        onDone: (Int) -> Unit = {}
    ) {
        scope.launch {
            val db = dbHelper.writableDatabase
            var imported = 0
            db.beginTransaction()
            try {
                val accountsList = readAccounts(db).toMutableList()
                val existingTxs = readTransactions(db).toMutableList()

                for (cand in candidates.sortedBy { it.timestamp }) {
                    val p = cand.parsedTx
                    val preMatched = cand.matchedAccount?.let { m ->
                        accountsList.firstOrNull { it.id == m.id }?.takeIf {
                            SmsMessageParser.matchAccount(listOf(it), p) != null
                        }
                    }
                    val matchedAccount = preMatched ?: SmsMessageParser.matchAccount(accountsList, p)

                    // Re-check deduplication inside the DB transaction against live + newly imported transactions
                    if (SmsMessageParser.isDuplicateTransaction(p, matchedAccount?.id, cand.timestamp, existingTxs)) {
                        logIngestEvent(db, cand.timestamp, cand.sender, p.rawMessage, "IGNORED", "Duplicate transaction during backfill")
                        continue
                    }

                    val account = matchedAccount ?: createAccountForParsedMessage(db, p)?.also { created ->
                        accountsList.add(created)
                    }
                    if (account == null) {
                        logIngestEvent(db, cand.timestamp, cand.sender, p.rawMessage, "FAILED", "Could not resolve or create account during backfill")
                        continue
                    }

                    val targetAccount = if (p.type == TransactionType.TRANSFER) {
                        resolveTargetAccountForParsedTransfer(db, accountsList, account.id, p)?.also { target ->
                            if (accountsList.none { it.id == target.id }) {
                                accountsList.add(target)
                            }
                        }
                    } else null

                    val item = TransactionItem(
                        title = p.title,
                        amountCentavos = p.amountCentavos,
                        type = p.type,
                        accountId = account.id,
                        targetAccountId = targetAccount?.id,
                        category = p.category,
                        timestamp = cand.timestamp,
                        note = null,
                        sourceInfo = p.sourceInfo,
                        refNumber = p.refNumber,
                        dedupKey = p.dedupKey
                    )
                    val insertedId = insertTransactionRow(db, item)
                    existingTxs.add(item.copy(id = insertedId))

                    val syncOutcome = SmsMessageParser.computeBalanceSyncOutcome(account, p, cand.timestamp)
                    if (syncOutcome.applyTxDelta) {
                        applyTransactionBalanceImpact(db, item, reverse = false)
                    } else if (targetAccount != null) {
                        adjustAccount(db, targetAccount.id, p.amountCentavos)
                    }
                    if (syncOutcome.adjustmentTransaction != null) {
                        val adjId = insertTransactionRow(db, syncOutcome.adjustmentTransaction)
                        applyTransactionBalanceImpact(db, syncOutcome.adjustmentTransaction, reverse = false)
                        existingTxs.add(syncOutcome.adjustmentTransaction.copy(id = adjId))
                    }

                    db.execSQL(
                        "UPDATE ${SaanNaPuntaDatabaseHelper.TABLE_ACCOUNTS} SET balanceAsOf = ? WHERE id = ?",
                        arrayOf<Any>(syncOutcome.newBalanceAsOf, account.id)
                    )

                    val idx = accountsList.indexOfFirst { it.id == account.id }
                    if (idx >= 0) {
                        accountsList[idx] = account.copy(
                            balanceCentavos = syncOutcome.newBalanceCentavos,
                            balanceAsOf = syncOutcome.newBalanceAsOf
                        )
                    }
                    if (targetAccount != null) {
                        val tIdx = accountsList.indexOfFirst { it.id == targetAccount.id }
                        if (tIdx >= 0) {
                            val currentTarget = accountsList[tIdx]
                            val delta = if (currentTarget.type == AccountType.LIABILITY) -p.amountCentavos else p.amountCentavos
                            accountsList[tIdx] = currentTarget.copy(balanceCentavos = currentTarget.balanceCentavos + delta)
                        }
                    }
                    imported++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            loadFromDb()
            onDone(imported)
        }
    }

}
