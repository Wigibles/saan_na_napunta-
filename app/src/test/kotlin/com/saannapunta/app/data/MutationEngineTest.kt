package com.saannapunta.app.data

import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.PayCycleConfig
import com.saannapunta.app.data.model.PayCycleType
import com.saannapunta.app.data.model.SavingsGoal
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.data.model.UpcomingBill
import com.saannapunta.app.data.model.isCountableSpend
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

class MutationEngineTest {

    private class FakeMutationStore(
        accounts: List<Account> = emptyList(),
        transactions: List<TransactionItem> = emptyList(),
        bills: List<UpcomingBill> = emptyList(),
        goals: List<SavingsGoal> = emptyList(),
        private val budgetCategories: List<String> = listOf("Utilities", "Food & Dining", "Personal")
    ) : MutationStore {
        val accountsById = accounts.associateBy { it.id }.toMutableMap()
        val transactionsById = transactions.associateBy { it.id }.toMutableMap()
        val billsById = bills.associateBy { it.id }.toMutableMap()
        val goalsById = goals.associateBy { it.id }.toMutableMap()
        private var nextTxId = (transactions.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun getAccountById(id: Long): Account? = accountsById[id]

        override fun addAccountBalanceDelta(id: Long, storedDeltaCentavos: Long) {
            val acc = accountsById[id] ?: throw IllegalArgumentException("Account with id=$id does not exist")
            accountsById[id] = acc.copy(balanceCentavos = acc.balanceCentavos + storedDeltaCentavos)
        }

        override fun archiveAccount(id: Long, archivedAt: Long) {
            val acc = accountsById[id] ?: return
            accountsById[id] = acc.copy(
                archivedAt = archivedAt,
                isSpendable = false,
                statusLine = "Archived account"
            )
        }

        override fun getTransactionById(id: Long): TransactionItem? = transactionsById[id]

        override fun insertTransaction(item: TransactionItem): Long {
            val id = if (item.id > 0L) item.id else nextTxId++
            transactionsById[id] = item.copy(id = id)
            return id
        }

        override fun updateTransactionRow(item: TransactionItem) {
            transactionsById[item.id] = item
        }

        override fun deleteTransactionRow(id: Long) {
            transactionsById.remove(id)
        }

        override fun getTransfersForAccount(accountId: Long): List<TransactionItem> =
            transactionsById.values.filter {
                it.type == TransactionType.TRANSFER &&
                    (it.accountId == accountId || it.targetAccountId == accountId)
            }

        override fun getUpcomingBillById(id: Long): UpcomingBill? = billsById[id]

        override fun updateUpcomingBillState(id: Long, isPaid: Boolean, dueDateTimestamp: Long, anchorDayOfMonth: Int?) {
            val bill = billsById[id] ?: return
            billsById[id] = bill.copy(
                isPaid = isPaid,
                dueDateTimestamp = dueDateTimestamp,
                anchorDayOfMonth = anchorDayOfMonth ?: bill.anchorDayOfMonth
            )
        }

        override fun getSavingsGoalById(id: Long): SavingsGoal? = goalsById[id]

        override fun updateSavingsGoalRow(goal: SavingsGoal) {
            goalsById[goal.id] = goal
        }

        override fun getBudgetCategories(): List<String> = budgetCategories
    }

    @Test
    fun testDoubleDeleteTransactionChangesBalanceOnlyOnceAndUsesDbRow() {
        val account = Account(
            id = 1L,
            name = "GCash",
            provider = "gcash",
            type = AccountType.E_WALLET,
            accountNumberMasked = "",
            balanceCentavos = 70_000L // ₱700.00 after a ₱300.00 expense
        )
        val dbTx = TransactionItem(
            id = 100L,
            title = "Jollibee",
            amountCentavos = 30_000L, // DB has ₱300.00
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "Food & Dining",
            timestamp = 1_000_000L
        )
        val store = FakeMutationStore(accounts = listOf(account), transactions = listOf(dbTx))

        // First delete restores ₱300.00 from DB row -> ₱1,000.00
        val firstDelete = MutationEngine.deleteTransaction(store, txId = 100L)
        assertTrue(firstDelete)
        assertEquals(100_000L, store.getAccountById(1L)?.balanceCentavos)
        assertNull(store.getTransactionById(100L))

        // Second delete (double tap) is a no-op: returns false and does not change balance again
        val secondDelete = MutationEngine.deleteTransaction(store, txId = 100L)
        assertFalse(secondDelete)
        assertEquals(100_000L, store.getAccountById(1L)?.balanceCentavos)
    }

    @Test
    fun testDoubleMarkBillPaidOneOffAndRecurringChangeBalanceOnlyOnce() {
        val account = Account(
            id = 1L,
            name = "BPI",
            provider = "bpi",
            type = AccountType.BANK,
            accountNumberMasked = "",
            balanceCentavos = 500_000L // ₱5,000.00
        )
        val oneOffBill = UpcomingBill(
            id = 10L,
            title = "Water Repair",
            amountCentavos = 50_000L, // ₱500.00
            dueDateTimestamp = 1_700_000_000_000L,
            isPaid = false,
            recurring = false,
            category = "Utilities"
        )
        val recurringBill = UpcomingBill(
            id = 20L,
            title = "Meralco",
            amountCentavos = 150_000L, // ₱1,500.00
            dueDateTimestamp = 1_700_000_000_000L,
            isPaid = false,
            recurring = true,
            category = "Utilities"
        )
        val store = FakeMutationStore(
            accounts = listOf(account),
            bills = listOf(oneOffBill, recurringBill)
        )

        // 1. One-off bill: double tap
        assertTrue(MutationEngine.markBillPaid(store, oneOffBill, accountId = 1L, nowMillis = 1_700_000_000_100L))
        assertEquals(450_000L, store.getAccountById(1L)?.balanceCentavos)
        assertFalse(MutationEngine.markBillPaid(store, oneOffBill, accountId = 1L, nowMillis = 1_700_000_000_200L))
        assertEquals(450_000L, store.getAccountById(1L)?.balanceCentavos)
        assertEquals(1, store.transactionsById.size)

        // 2. Recurring bill: double tap with the same UI snapshot `recurringBill`
        assertTrue(MutationEngine.markBillPaid(store, recurringBill, accountId = 1L, nowMillis = 1_700_000_000_300L))
        assertEquals(300_000L, store.getAccountById(1L)?.balanceCentavos)
        assertFalse(MutationEngine.markBillPaid(store, recurringBill, accountId = 1L, nowMillis = 1_700_000_000_400L))
        assertEquals(300_000L, store.getAccountById(1L)?.balanceCentavos)
        assertEquals(2, store.transactionsById.size)
    }

    @Test
    fun testUpdateTransactionReversesFromDbRowNotStaleUiSnapshot() {
        val account = Account(
            id = 1L,
            name = "GCash",
            provider = "gcash",
            type = AccountType.E_WALLET,
            accountNumberMasked = "",
            balanceCentavos = 80_000L // ₱800.00 after ₱200.00 DB expense
        )
        val dbTx = TransactionItem(
            id = 55L,
            title = "Coffee",
            amountCentavos = 20_000L, // DB has ₱200.00
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "Food & Dining",
            timestamp = 1_000L
        )
        val store = FakeMutationStore(accounts = listOf(account), transactions = listOf(dbTx))

        val updatedTx = dbTx.copy(amountCentavos = 35_000L) // Updated to ₱350.00
        assertTrue(MutationEngine.updateTransaction(store, txId = 55L, newItem = updatedTx))
        // 800 + 200 (reversed from DB) - 350 (new) = 650 (65,000 centavos)
        assertEquals(65_000L, store.getAccountById(1L)?.balanceCentavos)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAdjustAccountFailsLoudlyWhenAccountMissing() {
        val store = FakeMutationStore()
        MutationEngine.adjustAccount(store, accountId = 999L, moneyIn = 10_000L)
    }

    @Test
    fun testDeleteAccountArchivesAndPreservesSpendingHistory() {
        val bpi = Account(
            id = 1L,
            name = "BPI",
            provider = "bpi",
            type = AccountType.BANK,
            accountNumberMasked = "",
            balanceCentavos = 200_000L
        )
        val gcash = Account(
            id = 2L,
            name = "GCash",
            provider = "gcash",
            type = AccountType.E_WALLET,
            accountNumberMasked = "",
            balanceCentavos = 150_000L // Includes ₱500 transfer from BPI
        )
        val expenseOnBpi = TransactionItem(
            id = 1L,
            title = "Groceries",
            amountCentavos = 80_000L,
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "Food & Dining",
            timestamp = 1_000L
        )
        val transferBpiToGcash = TransactionItem(
            id = 2L,
            title = "Cash In",
            amountCentavos = 50_000L,
            type = TransactionType.TRANSFER,
            accountId = 1L,
            targetAccountId = 2L,
            category = "Transfer",
            timestamp = 2_000L
        )

        val store = FakeMutationStore(
            accounts = listOf(bpi, gcash),
            transactions = listOf(expenseOnBpi, transferBpiToGcash)
        )

        assertTrue(MutationEngine.deleteAccount(store, accountId = 1L, nowMillis = 9_999L))
        // Account 1 is archived (not deleted), non-spendable
        val archivedBpi = store.getAccountById(1L)
        assertNotNull(archivedBpi)
        assertEquals(9_999L, archivedBpi?.archivedAt)
        assertEquals(false, archivedBpi?.isSpendable)

        // Historical expense on BPI is preserved for spending & insights
        assertNotNull(store.getTransactionById(1L))
        // Transfer history is kept and the surviving account's real balance is untouched
        assertNotNull(store.getTransactionById(2L))
        assertEquals(150_000L, store.getAccountById(2L)?.balanceCentavos)

        // Second deleteAccount call is a no-op
        assertFalse(MutationEngine.deleteAccount(store, accountId = 1L, nowMillis = 10_000L))
        assertEquals(150_000L, store.getAccountById(2L)?.balanceCentavos)
    }

    @Test
    fun testCountableSpendExcludesAdjustmentsAndCreditCardPayments() {
        fun tx(type: TransactionType, category: String) = TransactionItem(
            title = "t", amountCentavos = 100L, type = type, accountId = 1L, category = category
        )
        assertTrue(tx(TransactionType.EXPENSE, "Food & Dining").isCountableSpend())
        assertFalse(tx(TransactionType.EXPENSE, "Adjustment").isCountableSpend())
        assertFalse(tx(TransactionType.EXPENSE, "credit card payment").isCountableSpend())
        assertFalse(tx(TransactionType.INCOME, "Income").isCountableSpend())
        assertFalse(tx(TransactionType.TRANSFER, "Transfer").isCountableSpend())
    }

    @Test
    fun testSavingsGoalContributionIsEarmarkNotExpense() {
        val bpi = Account(
            id = 1L,
            name = "BPI",
            provider = "bpi",
            type = AccountType.BANK,
            accountNumberMasked = "",
            balanceCentavos = 1_000_000L // ₱10,000.00
        )
        val goal = SavingsGoal(
            id = 7L,
            name = "Emergency Fund",
            targetCentavos = 5_000_000L,
            savedCentavos = 100_000L
        )
        val store = FakeMutationStore(accounts = listOf(bpi), goals = listOf(goal))

        assertTrue(MutationEngine.contributeToSavingsGoal(store, goalId = 7L, amountCentavos = 200_000L, fromAccountId = 1L))
        // Goal saved amount increases and is earmarked to account 1
        assertEquals(300_000L, store.getSavingsGoalById(7L)?.savedCentavos)
        assertEquals(1L, store.getSavingsGoalById(7L)?.accountId)
        // No EXPENSE transaction is created
        assertTrue(store.transactionsById.isEmpty())
        // Account's raw balance (for net worth) is unchanged
        assertEquals(1_000_000L, store.getAccountById(1L)?.balanceCentavos)
    }

    @Test
    fun testTransferDetectionAndConfirmation() {
        val bpi = Account(id = 1L, name = "BPI", provider = "bpi", type = AccountType.BANK, balanceCentavos = 800_000L)
        val gcash = Account(id = 2L, name = "GCash", provider = "gcash", type = AccountType.E_WALLET, balanceCentavos = 300_000L)

        // EXPENSE on BPI + INCOME on GCash of same amount within 2 minutes -> suggested as PossibleTransfer
        val exp = TransactionItem(
            id = 11L,
            title = "Send to GCash",
            amountCentavos = 200_000L,
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "Personal",
            timestamp = 1_000_000L
        )
        val inc = TransactionItem(
            id = 12L,
            title = "Received from BPI",
            amountCentavos = 200_000L,
            type = TransactionType.INCOME,
            accountId = 2L,
            category = "Income",
            timestamp = 1_120_000L // +2 mins (<= 5 mins)
        )
        val store = FakeMutationStore(accounts = listOf(bpi, gcash), transactions = listOf(exp, inc))

        val suggestions = MutationEngine.detectPossibleTransfers(store.transactionsById.values.toList())
        assertEquals(1, suggestions.size)
        assertEquals(11L, suggestions.first().expenseTx.id)
        assertEquals(12L, suggestions.first().incomeTx.id)

        // User confirms the transfer: replaces the pair with a single TRANSFER without changing net balances
        assertTrue(MutationEngine.confirmPossibleTransfer(store, expenseTxId = 11L, incomeTxId = 12L))
        assertNull(store.getTransactionById(11L))
        assertNull(store.getTransactionById(12L))
        assertEquals(1, store.transactionsById.size)
        val merged = store.transactionsById.values.single()
        assertEquals(TransactionType.TRANSFER, merged.type)
        assertEquals(1L, merged.accountId)
        assertEquals(2L, merged.targetAccountId)
        assertEquals(200_000L, merged.amountCentavos)
        assertEquals(800_000L, store.getAccountById(1L)?.balanceCentavos)
        assertEquals(300_000L, store.getAccountById(2L)?.balanceCentavos)
    }

    @Test
    fun testManualEntryWarnsOnSimilarAutoIngestedTransaction() {
        val autoIngested = TransactionItem(
            id = 1L,
            title = "Jollibee",
            amountCentavos = 28900L,
            type = TransactionType.EXPENSE,
            accountId = 10L,
            category = "Food & Dining",
            timestamp = 10_000_000L,
            sourceInfo = "From GCash message"
        )
        val match = MutationEngine.findSimilarAutoIngestedTransaction(
            accountId = 10L,
            amountCentavos = 28900L,
            type = TransactionType.EXPENSE,
            nowMillis = 10_600_000L,
            existingTransactions = listOf(autoIngested)
        )
        assertNotNull(match)
        assertEquals(1L, match?.id)
    }

    @Test
    fun testCyclesFebruarySundayPaydayDecToJanRolloverMonthlyEndWeeklyFlexible() {
        val semiMonthlyWeekend = PayCycleConfig(
            type = PayCycleType.SEMI_MONTHLY_15_30,
            firstPayday = 15,
            secondPayday = 30,
            payFridayBeforeWeekend = true
        )

        // 1. February + Sunday payday + Saturday month-end:
        // In Feb 2026: Feb 15 is Sunday -> shifts to Friday Feb 13.
        // Feb 28 (30 clamped to 28) is Saturday -> shifts to Friday Feb 27.
        val (febStart, febNext) = CycleCalculator.calculateActiveCycle(
            LocalDate.of(2026, 2, 16),
            semiMonthlyWeekend
        )
        assertEquals(LocalDate.of(2026, 2, 13), febStart)
        assertEquals(LocalDate.of(2026, 2, 27), febNext)

        // Without weekend rule in February: Feb 15 to Feb 28
        val semiMonthlyExact = semiMonthlyWeekend.copy(payFridayBeforeWeekend = false)
        val (febExactStart, febExactNext) = CycleCalculator.calculateActiveCycle(
            LocalDate.of(2026, 2, 20),
            semiMonthlyExact
        )
        assertEquals(LocalDate.of(2026, 2, 15), febExactStart)
        assertEquals(LocalDate.of(2026, 2, 28), febExactNext)

        // 2. Dec to Jan rollover (both Dec 31 and Jan 5 belong to Dec 30 -> Jan 15 cycle)
        val (decRollStart, decRollNext) = CycleCalculator.calculateActiveCycle(
            LocalDate.of(2026, 12, 31),
            semiMonthlyWeekend
        )
        assertEquals(LocalDate.of(2026, 12, 30), decRollStart)
        assertEquals(LocalDate.of(2027, 1, 15), decRollNext)

        val (janRollStart, janRollNext) = CycleCalculator.calculateActiveCycle(
            LocalDate.of(2027, 1, 5),
            semiMonthlyWeekend
        )
        assertEquals(LocalDate.of(2026, 12, 30), janRollStart)
        assertEquals(LocalDate.of(2027, 1, 15), janRollNext)

        // 3. MONTHLY_END (with weekend rule: Jan 31, 2026 is Saturday -> Friday Jan 30; Feb 28, 2026 is Saturday -> Friday Feb 27)
        val monthlyEndCfg = PayCycleConfig(type = PayCycleType.MONTHLY_END, payFridayBeforeWeekend = true)
        val (meStart, meNext) = CycleCalculator.calculateActiveCycle(
            LocalDate.of(2026, 2, 10),
            monthlyEndCfg
        )
        assertEquals(LocalDate.of(2026, 1, 30), meStart)
        assertEquals(LocalDate.of(2026, 2, 27), meNext)

        // 4. WEEKLY (resets Monday)
        val weeklyCfg = PayCycleConfig(type = PayCycleType.WEEKLY, firstPayday = Calendar.MONDAY)
        val (wkStart, wkNext) = CycleCalculator.calculateActiveCycle(
            LocalDate.of(2026, 11, 4), // Wednesday
            weeklyCfg
        )
        assertEquals(LocalDate.of(2026, 11, 2), wkStart) // Monday
        assertEquals(LocalDate.of(2026, 11, 9), wkNext)  // Next Monday

        // 5. FLEXIBLE (rolling monthly on day 10)
        val flexibleCfg = PayCycleConfig(type = PayCycleType.FLEXIBLE, firstPayday = 10)
        val (flexBeforeStart, flexBeforeNext) = CycleCalculator.calculateActiveCycle(
            LocalDate.of(2026, 11, 5),
            flexibleCfg
        )
        assertEquals(LocalDate.of(2026, 10, 10), flexBeforeStart)
        assertEquals(LocalDate.of(2026, 11, 10), flexBeforeNext)
    }

    @Test
    fun testSafeToSpendUnpaidBillsSubtractedCappedByBalanceAndOverspendFlagged() {
        val nowDate = LocalDate.of(2026, 11, 1)
        val nowMillis = CycleCalculator.startOfDayMillis(nowDate) + 3_600_000L
        val cycleConfig = PayCycleConfig(
            type = PayCycleType.SEMI_MONTHLY_15_30,
            firstPayday = 15,
            secondPayday = 30,
            payFridayBeforeWeekend = false
        )
        // Cycle is Oct 30 .. Nov 14 (next payday Nov 15 -> 14 days left from Nov 1)

        // Case 1: Unpaid bills due before cycle end are subtracted from min(budgetRemainder, spendableBalance)
        val richAccount = Account(
            id = 1L,
            name = "BPI",
            provider = "bpi",
            type = AccountType.BANK,
            balanceCentavos = 5_000_000L, // ₱50,000.00
            isSpendable = true
        )
        val budget = CategoryBudget(id = 1L, category = "Food & Dining", limitCentavos = 1_400_000L) // ₱14,000.00
        val spentInCycle = TransactionItem(
            id = 1L,
            title = "Groceries",
            amountCentavos = 400_000L, // ₱4,000.00 spent -> budget remainder = ₱10,000.00
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "Food & Dining",
            timestamp = nowMillis
        )
        val billDueInCycle = UpcomingBill(
            id = 10L,
            title = "Meralco",
            amountCentavos = 300_000L, // ₱3,000.00 due Nov 10 (inside cycle)
            dueDateTimestamp = CycleCalculator.startOfDayMillis(LocalDate.of(2026, 11, 10)),
            isPaid = false,
            recurring = true,
            category = "Utilities"
        )
        val billDueNextCycle = UpcomingBill(
            id = 11L,
            title = "Condo Dues",
            amountCentavos = 500_000L, // Due Nov 25 (after cycle end Nov 14 -> not subtracted in this cycle)
            dueDateTimestamp = CycleCalculator.startOfDayMillis(LocalDate.of(2026, 11, 25)),
            isPaid = false,
            recurring = true,
            category = "Utilities"
        )

        val overviewWithBills = CycleCalculator.computeOverviewSnapshot(
            accList = listOf(richAccount),
            txList = listOf(spentInCycle),
            budgetList = listOf(budget),
            goalsList = emptyList(),
            upcomingBills = listOf(billDueInCycle, billDueNextCycle),
            cycleConfig = cycleConfig,
            nowMillis = nowMillis
        )
        assertEquals(14, overviewWithBills.daysLeftInCycle)
        // min(10,000, 50,000) - 3,000 bill = 7,000 (700,000 centavos) / 14 days = 50,000 centavos/day (₱500/day)
        assertEquals(50_000L, overviewWithBills.safeToSpendPerDayCentavos)
        assertFalse(overviewWithBills.isOverBudget)
        assertEquals(0L, overviewWithBills.overspentCentavos)

        // Case 2: Capped by spendable balance when spendable balance < budget remainder
        val lowBalanceAccount = richAccount.copy(balanceCentavos = 580_000L) // ₱5,800.00 (< ₱10,000.00 budget remainder)
        val overviewCappedByBalance = CycleCalculator.computeOverviewSnapshot(
            accList = listOf(lowBalanceAccount),
            txList = listOf(spentInCycle),
            budgetList = listOf(budget),
            goalsList = emptyList(),
            upcomingBills = listOf(billDueInCycle),
            cycleConfig = cycleConfig,
            nowMillis = nowMillis
        )
        // min(10,000, 5,800) - 3,000 bill = 2,800 (280,000 centavos) / 14 days = 20,000 centavos/day (₱200/day)
        assertEquals(20_000L, overviewCappedByBalance.safeToSpendPerDayCentavos)

        // Case 3: Overspend flagged (isOverBudget = true, overspentCentavos exposed, safeToSpendPerDay floored at 0)
        val overspentTx = spentInCycle.copy(amountCentavos = 1_650_000L) // ₱16,500.00 spent vs ₱14,000.00 budget
        val overviewOverspent = CycleCalculator.computeOverviewSnapshot(
            accList = listOf(richAccount),
            txList = listOf(overspentTx),
            budgetList = listOf(budget),
            goalsList = emptyList(),
            upcomingBills = emptyList(),
            cycleConfig = cycleConfig,
            nowMillis = nowMillis
        )
        assertTrue(overviewOverspent.isOverBudget)
        assertEquals(250_000L, overviewOverspent.overspentCentavos) // ₱2,500.00 overspent
        assertEquals(-250_000L, overviewOverspent.leftThisCycleCentavos) // Not hidden by max(0, ...)
        assertEquals(0L, overviewOverspent.safeToSpendPerDayCentavos) // Floored at 0
    }

    @Test
    fun testRecurringBillAnchorDayDoesNotDriftAfterFebruaryAndOverdueBillAdvancesPastToday() {
        val account = Account(
            id = 1L,
            name = "BPI",
            provider = "bpi",
            type = AccountType.BANK,
            balanceCentavos = 2_000_000L
        )
        // 1. Recurring bill due on Jan 31, 2026 -> Feb 28, 2026 -> Mar 31, 2026 (no drift to 28th)
        val jan31Millis = CycleCalculator.startOfDayMillis(LocalDate.of(2026, 1, 31))
        val bill31 = UpcomingBill(
            id = 31L,
            title = "Internet Bill",
            amountCentavos = 169_900L,
            dueDateTimestamp = jan31Millis,
            isPaid = false,
            recurring = true,
            category = "Utilities"
        )
        val store = FakeMutationStore(accounts = listOf(account), bills = listOf(bill31))

        // Pay on Jan 31, 2026 -> advances to Feb 28, 2026 with anchorDayOfMonth = 31
        assertTrue(MutationEngine.markBillPaid(store, bill31, accountId = 1L, nowMillis = jan31Millis))
        val afterJan = store.getUpcomingBillById(31L)!!
        assertEquals(LocalDate.of(2026, 2, 28), CycleCalculator.toLocalDate(afterJan.dueDateTimestamp))
        assertEquals(31, afterJan.anchorDayOfMonth)

        // Pay on Feb 28, 2026 -> advances back to Mar 31, 2026 (not Mar 28!)
        val feb28Millis = CycleCalculator.startOfDayMillis(LocalDate.of(2026, 2, 28))
        assertTrue(MutationEngine.markBillPaid(store, afterJan, accountId = 1L, nowMillis = feb28Millis))
        val afterFeb = store.getUpcomingBillById(31L)!!
        assertEquals(LocalDate.of(2026, 3, 31), CycleCalculator.toLocalDate(afterFeb.dueDateTimestamp))
        assertEquals(31, afterFeb.anchorDayOfMonth)

        // 2. Overdue bill due Jan 15, 2026 paid on Mar 20, 2026 -> advances to Apr 15, 2026 (after today)
        val jan15Millis = CycleCalculator.startOfDayMillis(LocalDate.of(2026, 1, 15))
        val overdueBill = UpcomingBill(
            id = 40L,
            title = "Water Bill",
            amountCentavos = 45_000L,
            dueDateTimestamp = jan15Millis,
            isPaid = false,
            recurring = true,
            category = "Utilities",
            anchorDayOfMonth = 15
        )
        store.billsById[40L] = overdueBill
        val mar20Millis = CycleCalculator.startOfDayMillis(LocalDate.of(2026, 3, 20))
        assertTrue(MutationEngine.markBillPaid(store, overdueBill, accountId = 1L, nowMillis = mar20Millis))
        val afterOverdue = store.getUpcomingBillById(40L)!!
        assertEquals(LocalDate.of(2026, 4, 15), CycleCalculator.toLocalDate(afterOverdue.dueDateTimestamp))
    }
}

