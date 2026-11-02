package com.saannapunta.app.data.model

import java.text.NumberFormat
import java.util.Locale

enum class AccountType {
    E_WALLET,
    BANK,
    CASH,
    LIABILITY
}

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class PayCycleType {
    SEMI_MONTHLY_15_30,
    MONTHLY_END,
    WEEKLY,
    FLEXIBLE
}

data class Account(
    val id: Long = 0,
    val name: String,
    val provider: String,
    val type: AccountType,
    val accountNumberMasked: String = "",
    val balanceCentavos: Long = 0,
    val isSpendable: Boolean = true,
    val statusLine: String = "Manual account",
    val iconResName: String? = null,
    val balanceAsOf: Long = 0L,
    val needsOpeningBalance: Boolean = false,
    val archivedAt: Long? = null
)

data class TransactionItem(
    val id: Long = 0,
    val title: String,
    val amountCentavos: Long,
    val type: TransactionType,
    val accountId: Long,
    val targetAccountId: Long? = null,
    val category: String,
    val subCategory: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val sourceInfo: String = "Manual",
    val refNumber: String? = null,
    val dedupKey: String? = null
)

/**
 * True for expenses that count as real spending. Credit card payments (moving money to a liability)
 * and balance-sync adjustments are excluded so every screen agrees with the cycle overview.
 */
fun TransactionItem.isCountableSpend(): Boolean =
    type == TransactionType.EXPENSE &&
        !category.equals("Credit Card Payment", ignoreCase = true) &&
        !category.equals("Adjustment", ignoreCase = true)

data class IngestLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val sender: String,
    val bodyHash: String,
    val status: String,
    val reason: String
)

data class CategoryBudget(
    val id: Long = 0,
    val category: String,
    val limitCentavos: Long,
    val examples: String = "",
    val iconName: String? = null,
    val colorHex: String? = null,
    val subCategories: List<String> = emptyList()
)

data class SavingsGoal(
    val id: Long = 0,
    val name: String,
    val targetCentavos: Long,
    val savedCentavos: Long,
    val accountId: Long? = null
)

data class PossibleTransferSuggestion(
    val expenseTx: TransactionItem,
    val incomeTx: TransactionItem,
    val amountCentavos: Long = expenseTx.amountCentavos
) {
    val pairKey: String
        get() = "${expenseTx.id}:${incomeTx.id}"
}

data class UpcomingBill(
    val id: Long = 0,
    val title: String,
    val amountCentavos: Long,
    val dueDateTimestamp: Long,
    val isPaid: Boolean = false,
    /** Recurring bills roll forward one month when marked paid instead of disappearing. */
    val recurring: Boolean = true,
    val category: String = "Utilities",
    val subCategory: String? = null,
    /** Anchor day-of-month (1..31) so a 31st bill does not drift to the 28th after February. */
    val anchorDayOfMonth: Int? = null,
    /** Tracks the last reminder sent for this bill and due date (`"<dueEpochDay>:<STAGE>:<remindedEpochDay>"`). */
    val lastRemindedFor: String? = null
)

data class PayCycleConfig(
    val type: PayCycleType = PayCycleType.SEMI_MONTHLY_15_30,
    val firstPayday: Int = 15,
    val secondPayday: Int = 30,
    val payFridayBeforeWeekend: Boolean = true
)

object MoneyFormatter {
    private val format = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val wholeFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    fun format(centavos: Long, includeDecimals: Boolean = true): String {
        val pesos = centavos.toDouble() / 100.0
        val numStr = if (includeDecimals) format.format(pesos) else wholeFormat.format(pesos)
        return "₱$numStr"
    }

    fun formatWithSign(centavos: Long, type: TransactionType, includeDecimals: Boolean = true): String {
        val pesosAbs = Math.abs(centavos).toDouble() / 100.0
        val numStr = if (includeDecimals) format.format(pesosAbs) else wholeFormat.format(pesosAbs)
        return when (type) {
            TransactionType.EXPENSE -> "−₱$numStr"
            TransactionType.INCOME -> "+₱$numStr"
            TransactionType.TRANSFER -> "₱$numStr"
        }
    }
}

