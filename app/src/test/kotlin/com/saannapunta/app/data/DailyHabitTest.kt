package com.saannapunta.app.data

import com.saannapunta.app.data.db.SaanNaPuntaDatabaseHelper
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.PayCycleConfig
import com.saannapunta.app.data.model.PayCycleType
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import com.saannapunta.app.data.model.UpcomingBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class DailyHabitTest {

    @Test
    fun testLeftTodayUsesDailyAllowanceSnapshotAndDoesNotShrinkDayAllowanceAsYouSpend() {
        val zone = CycleCalculator.MANILA_ZONE
        val day1Morning = ZonedDateTime.of(2026, 11, 5, 8, 30, 0, 0, zone).toInstant().toEpochMilli()
        val day1Noon = ZonedDateTime.of(2026, 11, 5, 12, 15, 0, 0, zone).toInstant().toEpochMilli()
        val day2Morning = ZonedDateTime.of(2026, 11, 6, 8, 0, 0, 0, zone).toInstant().toEpochMilli()

        val initialAccount = Account(
            id = 1L,
            name = "Cash on Hand",
            provider = "cash",
            type = AccountType.CASH,
            balanceCentavos = 1_000_000L // ₱10,000
        )
        val budgets = listOf(
            CategoryBudget(id = 1L, category = "Food & Dining", limitCentavos = 1_000_000L) // ₱10,000 budget
        )
        val cycleConfig = PayCycleConfig(
            type = PayCycleType.SEMI_MONTHLY_15_30,
            firstPayday = 15,
            secondPayday = 30,
            payFridayBeforeWeekend = false
        )

        // 1. First computation after midnight on Nov 5 (10 days left until Nov 15):
        // safePerDay = ₱10,000 / 10 = ₱1,000 (100_000 centavos)
        val morningSnapshot = CycleCalculator.computeOverviewSnapshot(
            accList = listOf(initialAccount),
            txList = emptyList(),
            budgetList = budgets,
            goalsList = emptyList(),
            upcomingBills = emptyList(),
            cycleConfig = cycleConfig,
            nowMillis = day1Morning,
            zone = zone,
            storedDayAllowanceDate = null,
            storedDayAllowanceCentavos = null
        )
        assertEquals("2026-11-05", morningSnapshot.dayAllowanceDate)
        assertEquals(100_000L, morningSnapshot.safeToSpendPerDayCentavos)
        assertEquals(100_000L, morningSnapshot.dayAllowanceCentavos)
        assertEquals(100_000L, morningSnapshot.leftTodayCentavos)

        // 2. User spends ₱300 at noon on Nov 5:
        // safePerDay shrinks to (₱10,000 - ₱300) / 10 = ₱970,
        // but dayAllowance stays locked at ₱1,000 and leftToday is max(0, ₱1,000 - ₱300) = ₱700!
        val lunchTx = TransactionItem(
            id = 1L,
            title = "Lunch",
            amountCentavos = 30_000L,
            type = TransactionType.EXPENSE,
            accountId = 1L,
            category = "Food & Dining",
            timestamp = day1Noon
        )
        val afterLunchAccount = initialAccount.copy(balanceCentavos = 970_000L)

        val noonSnapshot = CycleCalculator.computeOverviewSnapshot(
            accList = listOf(afterLunchAccount),
            txList = listOf(lunchTx),
            budgetList = budgets,
            goalsList = emptyList(),
            upcomingBills = emptyList(),
            cycleConfig = cycleConfig,
            nowMillis = day1Noon,
            zone = zone,
            storedDayAllowanceDate = morningSnapshot.dayAllowanceDate,
            storedDayAllowanceCentavos = morningSnapshot.dayAllowanceCentavos
        )
        assertEquals(97_000L, noonSnapshot.safeToSpendPerDayCentavos)
        assertEquals(100_000L, noonSnapshot.dayAllowanceCentavos)
        assertEquals(70_000L, noonSnapshot.leftTodayCentavos)

        // 3. Next day (Nov 6, 9 days left until Nov 15): storedDayAllowanceDate is "2026-11-05" != "2026-11-06",
        // so a new daily snapshot is taken: (₱9,700 / 9) = 107_777 centavos, spentToday = 0.
        val day2Snapshot = CycleCalculator.computeOverviewSnapshot(
            accList = listOf(afterLunchAccount),
            txList = listOf(lunchTx),
            budgetList = budgets,
            goalsList = emptyList(),
            upcomingBills = emptyList(),
            cycleConfig = cycleConfig,
            nowMillis = day2Morning,
            zone = zone,
            storedDayAllowanceDate = morningSnapshot.dayAllowanceDate,
            storedDayAllowanceCentavos = morningSnapshot.dayAllowanceCentavos
        )
        assertEquals("2026-11-06", day2Snapshot.dayAllowanceDate)
        assertEquals(970_000L / 9L, day2Snapshot.dayAllowanceCentavos)
        assertEquals(day2Snapshot.dayAllowanceCentavos, day2Snapshot.leftTodayCentavos)
    }

    @Test
    fun testFastCashRecentCategoriesAndMostFrequentCategoryPerPresetAmount() {
        val txs = listOf(
            TransactionItem(id = 1L, title = "Jeepney", amountCentavos = 20_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Transportation", timestamp = 1_000L),
            TransactionItem(id = 2L, title = "Tricycle", amountCentavos = 20_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Transportation", timestamp = 2_000L),
            TransactionItem(id = 3L, title = "Snack", amountCentavos = 20_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Food & Dining", timestamp = 3_000L),
            TransactionItem(id = 4L, title = "Karinderya", amountCentavos = 50_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Food & Dining", timestamp = 4_000L),
            TransactionItem(id = 5L, title = "Coffee", amountCentavos = 100_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Coffee & Drinks", timestamp = 5_000L),
            TransactionItem(id = 6L, title = "Sari-sari", amountCentavos = 45_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Groceries", timestamp = 6_000L),
            TransactionItem(id = 7L, title = "Load", amountCentavos = 99_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Load & Data", timestamp = 7_000L),
            TransactionItem(id = 8L, title = "Balance Sync", amountCentavos = 10_00L, type = TransactionType.EXPENSE, accountId = 1L, category = "Adjustment", timestamp = 8_000L)
        )

        // 1. Top 4 most recent expense categories (excluding "Adjustment")
        val recent4 = DailyHabitEngine.getRecentExpenseCategories(txs, emptyList(), limit = 4)
        assertEquals(
            listOf("Load & Data", "Groceries", "Coffee & Drinks", "Food & Dining"),
            recent4
        )

        // 2. One-tap mode preset category frequency:
        // ₱20 was used twice for "Transportation" and once for "Food & Dining" -> "Transportation"
        assertEquals("Transportation", DailyHabitEngine.getMostFrequentCategoryForAmount(txs, 20_00L, recent4))
        // ₱50 was used for "Food & Dining" -> "Food & Dining"
        assertEquals("Food & Dining", DailyHabitEngine.getMostFrequentCategoryForAmount(txs, 50_00L, recent4))
        // ₱100 was used for "Coffee & Drinks" -> "Coffee & Drinks"
        assertEquals("Coffee & Drinks", DailyHabitEngine.getMostFrequentCategoryForAmount(txs, 100_00L, recent4))
    }

    @Test
    fun testBillRemindersConfigurableDaysBeforeDueDayAndWeeklyOverdueWithNoRepeats() {
        val zone = CycleCalculator.MANILA_ZONE
        val dueDate = LocalDate.of(2026, 11, 15)
        val dueMillis = CycleCalculator.startOfDayMillis(dueDate, zone)

        var bill = UpcomingBill(
            id = 42L,
            title = "Meralco",
            amountCentavos = 250_000L,
            dueDateTimestamp = dueMillis,
            isPaid = false,
            recurring = true
        )

        // 1. 4 days before due date (with default daysBefore = 3): too early, no reminder
        assertNull(DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 11), daysBefore = 3, zone = zone))

        // 2. 3 days before due date (Nov 12): triggers UPCOMING reminder once
        val decisionBefore = DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 12), daysBefore = 3, zone = zone)
        assertNotNull(decisionBefore)
        assertEquals(BillReminderStage.UPCOMING, decisionBefore!!.stage)
        assertEquals("2026-11-15:BEFORE:2026-11-12", decisionBefore.newLastRemindedFor)

        bill = bill.copy(lastRemindedFor = decisionBefore.newLastRemindedFor)

        // 3. 2 days and 1 day before due date (Nov 13, Nov 14): NO repeat since BEFORE reminder was already sent
        assertNull(DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 13), daysBefore = 3, zone = zone))
        assertNull(DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 14), daysBefore = 3, zone = zone))

        // 4. On the due day (Nov 15): triggers DUE_TODAY reminder once
        val decisionDue = DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 15), daysBefore = 3, zone = zone)
        assertNotNull(decisionDue)
        assertEquals(BillReminderStage.DUE_TODAY, decisionDue!!.stage)
        assertEquals("2026-11-15:DUE:2026-11-15", decisionDue.newLastRemindedFor)

        bill = bill.copy(lastRemindedFor = decisionDue.newLastRemindedFor)
        // Same due day again: no repeat
        assertNull(DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 15), daysBefore = 3, zone = zone))

        // 5. Overdue (Nov 16, 1 day overdue): sends first OVERDUE reminder
        val decisionOverdue1 = DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 16), daysBefore = 3, zone = zone)
        assertNotNull(decisionOverdue1)
        assertEquals(BillReminderStage.OVERDUE, decisionOverdue1!!.stage)
        assertEquals("2026-11-15:OVERDUE:2026-11-16", decisionOverdue1.newLastRemindedFor)

        bill = bill.copy(lastRemindedFor = decisionOverdue1.newLastRemindedFor)

        // 6. Days 2..6 overdue (Nov 17..Nov 22): suppressed (weekly cadence)
        for (day in 17..22) {
            assertNull(
                "Should not repeat overdue reminder on Nov $day",
                DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, day), daysBefore = 3, zone = zone)
            )
        }

        // 7. 7 days after first overdue reminder (Nov 23): triggers weekly OVERDUE reminder
        val decisionOverdueWeekly = DailyHabitEngine.shouldRemindBill(bill, LocalDate.of(2026, 11, 23), daysBefore = 3, zone = zone)
        assertNotNull(decisionOverdueWeekly)
        assertEquals(BillReminderStage.OVERDUE, decisionOverdueWeekly!!.stage)
        assertEquals("2026-11-15:OVERDUE:2026-11-23", decisionOverdueWeekly.newLastRemindedFor)
    }

    @Test
    fun testInitialDelayToNext8AmManilaAndV4ToV5SchemaUpgrade() {
        val zone = CycleCalculator.MANILA_ZONE

        // At 6:30 AM Manila -> 1.5 hours (90 minutes) until 8:00 AM today
        val sixThirtyAm = ZonedDateTime.of(2026, 11, 5, 6, 30, 0, 0, zone).toInstant().toEpochMilli()
        val delayFromMorning = DailyHabitEngine.computeInitialDelayToNext8AmManila(sixThirtyAm, zone)
        assertEquals(90L * 60_000L, delayFromMorning)

        // At 9:00 AM Manila -> 23 hours until 8:00 AM tomorrow
        val nineAm = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val delayFromAfter8Am = DailyHabitEngine.computeInitialDelayToNext8AmManila(nineAm, zone)
        assertEquals(23L * 60L * 60_000L, delayFromAfter8Am)

        // Schema v4 -> v5 adds lastRemindedFor to upcoming_bills
        val addedSql = mutableListOf<String>()
        val executor = object : SaanNaPuntaDatabaseHelper.SqlExecutor {
            override fun execSQL(sql: String) {
                addedSql.add(sql)
            }
            override fun deleteAll(table: String) {}
            override fun upsertSetting(key: String, value: String) {}
        }
        SaanNaPuntaDatabaseHelper.upgradeSchema(executor, oldVersion = 4, newVersion = 5)
        assertTrue(addedSql.any { it.contains("lastRemindedFor") })
    }
}

