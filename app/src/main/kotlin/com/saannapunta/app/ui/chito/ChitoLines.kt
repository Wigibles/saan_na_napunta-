package com.saannapunta.app.ui.chito

import com.saannapunta.app.data.MoneyOverview
import com.saannapunta.app.data.model.CategoryBudget
import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.TransactionType
import java.util.Calendar

/**
 * What Chito says on each screen. Plain, short, and about the user's own numbers (design spec
 * section 9): no exclamation marks, no praise, Taglish where people actually use it.
 */
object ChitoLines {

    private fun peso(centavos: Long) = MoneyFormatter.format(centavos, includeDecimals = false)

    private fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    /** Tips Chito cycles through when tapped on Home. */
    private val tips = listOf(
        ChitoLine(ChitoMood.POINTING, "Moving money between your own accounts is a transfer, not spending."),
        ChitoLine(ChitoMood.COUNTING, "Bills due before payday are set aside from your safe amount per day."),
        ChitoLine(ChitoMood.PIGGY, "Ipon tip: put a little into a savings goal right after sahod."),
        ChitoLine(ChitoMood.DETECTIVE, "Tap a transaction to fix its category if I guessed wrong."),
        ChitoLine(ChitoMood.PHONE, "Turn on message reading in Settings and I'll log GCash and bank texts for you."),
        ChitoLine(ChitoMood.LAPTOP, "Back up your data in Settings now and then. It only lives on this phone.")
    )

    fun home(overview: MoneyOverview, hasTransactions: Boolean, tapCount: Int): ChitoLine {
        if (tapCount > 0) return tips[(tapCount - 1) % tips.size]
        val h = hour()
        return when {
            !hasTransactions -> ChitoLine(ChitoMood.WAVING, "Add your first expense or income and I'll start pacing your money.")
            overview.daysLeftInCycle >= 13 && overview.totalSpentInCycleCentavos == 0L -> ChitoLine(
                ChitoMood.CONFETTI,
                "New cycle, fresh start. ${peso(overview.leftTodayCentavos)} is safe to spend today."
            )
            overview.isOverBudget -> ChitoLine(
                ChitoMood.FACEPALM,
                "You're ${peso(overview.overspentCentavos)} over this cycle's budget. Let's go easy until ${overview.nextPaydayText}."
            )
            overview.leftTodayCentavos <= 0L && overview.dayAllowanceCentavos > 0L -> ChitoLine(
                ChitoMood.BROKE,
                "Today's safe amount is used up. Anything more comes out of the next days."
            )
            h >= 22 || h < 5 -> ChitoLine(
                ChitoMood.SLEEPING,
                "Spent ${peso(overview.spentTodayCentavos)} today. Pahinga na, see you tomorrow."
            )
            overview.spentTodayCentavos == 0L -> ChitoLine(
                ChitoMood.RICH,
                "No spending yet today. You have ${peso(overview.leftTodayCentavos)} safe to spend."
            )
            overview.leftTodayCentavos < overview.dayAllowanceCentavos / 4 -> ChitoLine(
                ChitoMood.CONFUSED,
                "Only ${peso(overview.leftTodayCentavos)} left for today. ${overview.daysLeftInCycle} days until ${overview.nextPaydayText}."
            )
            h < 11 -> ChitoLine(
                ChitoMood.WAVING,
                "Magandang umaga. ${peso(overview.leftTodayCentavos)} is safe to spend today."
            )
            else -> ChitoLine(
                ChitoMood.THUMBSUP,
                "${peso(overview.leftTodayCentavos)} left for today, ${overview.daysLeftInCycle} days until ${overview.nextPaydayText}."
            )
        }
    }

    fun addTransaction(type: TransactionType, amountCentavos: Long, leftTodayCentavos: Long, isEditing: Boolean): ChitoLine {
        if (amountCentavos <= 0L) {
            return when (type) {
                TransactionType.EXPENSE -> ChitoLine(ChitoMood.THINKING, if (isEditing) "What should this be instead?" else "Magkano? Enter the amount first.")
                TransactionType.INCOME -> ChitoLine(ChitoMood.RICH, "Money in. How much did you get?")
                TransactionType.TRANSFER -> ChitoLine(ChitoMood.POINTING, "Moving money between your accounts doesn't count as spending.")
            }
        }
        return when (type) {
            TransactionType.INCOME -> ChitoLine(ChitoMood.HEARTEYES, "${peso(amountCentavos)} in. I'll add it to your balance.")
            TransactionType.TRANSFER -> ChitoLine(ChitoMood.THUMBSUP, "Moving ${peso(amountCentavos)}. Your total stays the same.")
            TransactionType.EXPENSE -> when {
                isEditing -> ChitoLine(ChitoMood.THINKING, "I'll update your balance with the new amount.")
                leftTodayCentavos <= 0L -> ChitoLine(ChitoMood.BROKE, "Today's safe amount is used up. This comes out of the next days.")
                amountCentavos > leftTodayCentavos -> ChitoLine(
                    ChitoMood.RUNNING,
                    "That's ${peso(amountCentavos - leftTodayCentavos)} over what's safe for today."
                )
                else -> ChitoLine(ChitoMood.PAYING, "That leaves ${peso(leftTodayCentavos - amountCentavos)} for today.")
            }
        }
    }

    /** Chito's pop-up after a transaction is saved, using the numbers from before the save. */
    fun saved(type: TransactionType, amountCentavos: Long, overviewBefore: MoneyOverview, isEditing: Boolean): ChitoLine {
        if (isEditing) return ChitoLine(ChitoMood.THUMBSUP, "Updated. Your balances are fixed too.")
        return when (type) {
            TransactionType.INCOME -> ChitoLine(ChitoMood.HEARTEYES, "${peso(amountCentavos)} in. Don't forget a little for ipon.")
            TransactionType.TRANSFER -> ChitoLine(ChitoMood.THUMBSUP, "Moved ${peso(amountCentavos)}. Your total stays the same.")
            TransactionType.EXPENSE -> {
                val limit = overviewBefore.totalBudgetLimitCentavos
                val spentAfter = overviewBefore.totalSpentInCycleCentavos + amountCentavos
                val leftToday = overviewBefore.leftTodayCentavos
                when {
                    limit > 0L && spentAfter > limit && overviewBefore.totalSpentInCycleCentavos <= limit ->
                        ChitoLine(ChitoMood.FACEPALM, "Ay. That puts you ${peso(spentAfter - limit)} over this cycle's budget.")
                    amountCentavos > leftToday ->
                        ChitoLine(ChitoMood.RUNNING, "${peso(amountCentavos)} logged. That's past today's safe amount.")
                    else ->
                        ChitoLine(ChitoMood.PAYING, "${peso(amountCentavos)} logged. ${peso(leftToday - amountCentavos)} left for today.")
                }
            }
        }
    }

    fun deleted(): ChitoLine = ChitoLine(ChitoMood.POINTING, "Removed. Your balance is back to what it was.")

    fun billPaid(title: String): ChitoLine = ChitoLine(ChitoMood.PAYING, "$title paid. One less thing to worry about.")

    fun goalDeposit(goalName: String, amountCentavos: Long, reachedTarget: Boolean): ChitoLine =
        if (reachedTarget) ChitoLine(ChitoMood.CONFETTI, "$goalName is fully funded. Ang galing ng ipon mo.")
        else ChitoLine(ChitoMood.PIGGY, "${peso(amountCentavos)} into $goalName.")

    fun budgets(budgets: List<CategoryBudget>, spentByCategory: Map<String, Long>): ChitoLine {
        if (budgets.isEmpty()) {
            return ChitoLine(ChitoMood.LAPTOP, "No category budgets yet. Add one and I'll watch it for you.")
        }
        val over = budgets
            .map { it to (spentByCategory[it.category] ?: 0L) }
            .filter { (b, spent) -> b.limitCentavos > 0 && spent > b.limitCentavos }
            .maxByOrNull { (b, spent) -> spent - b.limitCentavos }
        if (over != null) {
            val (b, spent) = over
            return ChitoLine(ChitoMood.FACEPALM, "${b.category} is ${peso(spent - b.limitCentavos)} over. Let's take a look before spending more there.")
        }
        val near = budgets.firstOrNull { b ->
            b.limitCentavos > 0 && (spentByCategory[b.category] ?: 0L) >= b.limitCentavos * 85 / 100
        }
        if (near != null) {
            val left = near.limitCentavos - (spentByCategory[near.category] ?: 0L)
            return ChitoLine(ChitoMood.CONFUSED, "${near.category} is almost used up, ${peso(left)} left.")
        }
        return ChitoLine(ChitoMood.LAPTOP, "Every category is within its limit. I'm keeping the sheet updated.")
    }

    fun accounts(netWorthCentavos: Long, accountCount: Int, needsBalance: Int): ChitoLine = when {
        accountCount == 0 -> ChitoLine(ChitoMood.WAVING, "Add the wallets and banks you use so I can see where your money is.")
        needsBalance > 0 -> ChitoLine(
            ChitoMood.THINKING,
            if (needsBalance == 1) "One account still needs its current balance." else "$needsBalance accounts still need their current balance."
        )
        netWorthCentavos < 0L -> ChitoLine(ChitoMood.BROKE, "You owe more than you have right now. Paying down cards first helps.")
        else -> ChitoLine(ChitoMood.COUNTING, "Net worth ${peso(netWorthCentavos)} across $accountCount accounts. Counted it twice.")
    }

    fun transactions(count: Int, spentTodayCentavos: Long): ChitoLine = when {
        count == 0 -> ChitoLine(ChitoMood.WAVING, "No transactions yet. Add one, or turn on message reading in Settings.")
        spentTodayCentavos == 0L -> ChitoLine(ChitoMood.RICH, "Nothing spent today so far.")
        else -> ChitoLine(ChitoMood.DETECTIVE, "Spent ${peso(spentTodayCentavos)} today. Tap any row to fix it.")
    }

    fun settings(lastBackup: String): ChitoLine =
        if (lastBackup == "Never") {
            ChitoLine(ChitoMood.CONFUSED, "Your data only lives on this phone. Export a backup so you don't lose it.")
        } else {
            ChitoLine(ChitoMood.PHONE, "Last backup: $lastBackup. Your data stays on this phone.")
        }

    fun bill(amountCentavos: Long, daysBefore: Int): ChitoLine =
        if (amountCentavos <= 0L) ChitoLine(ChitoMood.THINKING, "Which bill is coming up? I'll set the money aside for it.")
        else ChitoLine(ChitoMood.COUNTING, "I'll keep ${peso(amountCentavos)} aside and remind you $daysBefore days before.")

    fun payBill(title: String): ChitoLine = ChitoLine(ChitoMood.PAYING, "Paying $title. Pick the account it came from.")

    fun account(isEditing: Boolean): ChitoLine =
        if (isEditing) ChitoLine(ChitoMood.THINKING, "Changing the balance here sets it to exactly what you enter.")
        else ChitoLine(ChitoMood.WAVING, "Enter what's in this account right now.")

    fun categoryBudget(limitCentavos: Long): ChitoLine =
        if (limitCentavos <= 0L) ChitoLine(ChitoMood.LAPTOP, "Set a limit that feels doable for one pay cycle.")
        else ChitoLine(ChitoMood.THUMBSUP, "${peso(limitCentavos)} per cycle. I'll warn you at 85%.")

    fun quickCash(amountCentavos: Long): ChitoLine =
        if (amountCentavos <= 0L) ChitoLine(ChitoMood.THINKING, "Cash spent? Enter how much.")
        else ChitoLine(ChitoMood.PAYING, "Logging ${peso(amountCentavos)} from your cash.")

    fun backfill(found: Int): ChitoLine =
        if (found == 0) ChitoLine(ChitoMood.DETECTIVE, "I'll look through your bank and e-wallet texts. Personal messages are skipped.")
        else ChitoLine(ChitoMood.CONFETTI, "Found $found transactions. Uncheck any you don't want.")

    fun categories(): ChitoLine = ChitoLine(ChitoMood.LAPTOP, "Pick icons and colors so you can spot each category at a glance.")

    val savingsGoal = ChitoLine(ChitoMood.PIGGY, "Money you put in a goal is held back from your safe amount per day.")
}
