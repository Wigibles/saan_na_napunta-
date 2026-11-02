package com.saannapunta.app.parser

import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class SmsMessageParserTest {

    @Test
    fun testGCashPaidMessageAndRefExtraction() {
        val msg = "You have paid PHP 289.00 of GCash to JOLLIBEE GAPAN on 11/01/2026 12:45. Your new balance is PHP 14,850.50. Ref. No. 987654321."
        val parsed = SmsMessageParser.parse("GCash", msg)

        assertNotNull(parsed)
        assertEquals("Jollibee Gapan", parsed?.title)
        assertEquals(28900L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Food & Dining", parsed?.category)
        assertEquals(1485050L, parsed?.newBalanceCentavos)
        assertEquals("987654321", parsed?.refNumber)
        assertEquals("gcash:987654321", parsed?.dedupKey)
        assertEquals("From GCash message", parsed?.sourceInfo)
    }

    @Test
    fun testGCashSentMessage() {
        val msg = "You have sent PHP 1,500.00 of GCash to Juan Dela Cruz 09171234567 on 11/01/2026 14:30. Ref. No. 123456789."
        val parsed = SmsMessageParser.parse("2882", msg)

        assertNotNull(parsed)
        assertEquals(150000L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Personal", parsed?.category)
        assertEquals("123456789", parsed?.refNumber)
    }

    @Test
    fun testGCashReceivedMessage() {
        val msg = "You have received PHP 500.00 of GCash from Maria Santos on 11/01/2026 10:15."
        val parsed = SmsMessageParser.parse("GCash", msg)

        assertNotNull(parsed)
        assertEquals(50000L, parsed?.amountCentavos)
        assertEquals(TransactionType.INCOME, parsed?.type)
        assertEquals("Income", parsed?.category)
    }

    @Test
    fun testMayaPaidMessage() {
        val msg = "You paid PHP 2,400.00 to UNIQLO ROBINSONS on 11/01/2026 01:40 AM. Reference ID: 998877."
        val parsed = SmsMessageParser.parse("Maya", msg)

        assertNotNull(parsed)
        assertEquals("Uniqlo Robinsons", parsed?.title)
        assertEquals(240000L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Personal", parsed?.category)
        assertEquals("998877", parsed?.refNumber)
        assertEquals("maya:998877", parsed?.dedupKey)
        assertEquals("From Maya message", parsed?.sourceInfo)
    }

    @Test
    fun testMayaPaidWithReferenceCleaning() {
        val msg = "You paid PHP 51.50 to BEEPTM with reference no. 123456."
        val parsed = SmsMessageParser.parse("Maya", msg)

        assertNotNull(parsed)
        assertEquals("Beeptm", parsed?.title)
        assertEquals(5150L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Transportation", parsed?.category)
        assertEquals("123456", parsed?.refNumber)
    }

    @Test
    fun testBankChargedMessageExtractsLast4() {
        val msg = "Your BPI debit card ending in 7183 was charged PHP 2,310.00 at MERALCO on 11/01/2026."
        val parsed = SmsMessageParser.parse("BPI", msg)

        assertNotNull(parsed)
        assertEquals("Meralco", parsed?.title)
        assertEquals(231000L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Utilities", parsed?.category)
        assertEquals("7183", parsed?.accountLast4)
        assertEquals(false, parsed?.isCreditCard)
    }

    @Test
    fun testBankPurchaseMessage() {
        val msg = "Purchase of PHP 1,890.00 at UNIQLO MANILA using BDO Debit card on 10/30/2026."
        val parsed = SmsMessageParser.parse("BDO", msg)

        assertNotNull(parsed)
        assertEquals("Uniqlo Manila", parsed?.title)
        assertEquals(189000L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Personal", parsed?.category)
    }

    @Test
    fun testBankDebitedMessage() {
        val msg = "Your BPI account has been debited PHP 1,250.00 at MERCURY DRUG on 11/01/2026."
        val parsed = SmsMessageParser.parse("BPI", msg)

        assertNotNull(parsed)
        assertEquals("Mercury Drug", parsed?.title)
        assertEquals(125000L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Personal", parsed?.category)
    }

    @Test
    fun testAtmWithdrawalMessageIsTransfer() {
        val msg = "ATM withdrawal of PHP 3,000.00 at BPI AYALA on 10/25/2026."
        val parsed = SmsMessageParser.parse("BPI", msg)

        assertNotNull(parsed)
        assertTrue(parsed?.title?.startsWith("ATM Withdrawal") == true)
        assertEquals(300000L, parsed?.amountCentavos)
        assertEquals(TransactionType.TRANSFER, parsed?.type)
        assertEquals("Transfer", parsed?.category)
    }

    @Test
    fun testCreditCardPaymentNeverParsedAsExpense() {
        val msg = "You have paid PHP 4,500.00 of GCash to BPI CREDIT CARD on 11/01/2026. Ref. No. 556677."
        val parsed = SmsMessageParser.parse("GCash", msg)

        assertNotNull(parsed)
        assertEquals(450000L, parsed?.amountCentavos)
        assertEquals(TransactionType.TRANSFER, parsed?.type)
        assertEquals("Credit Card Payment", parsed?.category)
        assertEquals(false, parsed?.isCreditCard)
    }

    @Test
    fun testPushNotificationPackageAllowlist() {
        val msg = "You paid ₱480.00 to GRAB PH on 11/01/2026."
        val parsed = SmsMessageParser.parse("ph.gotyme.app", msg)

        assertNotNull(parsed)
        assertEquals("Grab Ph", parsed?.title)
        assertEquals(48000L, parsed?.amountCentavos)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("Transportation", parsed?.category)
        assertEquals(IngestionChannel.NOTIFICATION, parsed?.channel)
        assertEquals("From GoTyme notification", parsed?.sourceInfo)
    }

    @Test
    fun testSpoofedSenderAndBodyMentionsIgnored() {
        // Unknown phone number spoofing GCash / Maya in body must be ignored
        val spoofedGcash = "You have paid PHP 500.00 of GCash to STORE on 11/01/2026. Ref. No. 111222."
        assertNull(SmsMessageParser.parse("+639991112233", spoofedGcash))

        val spoofedMaya = "You paid PHP 900.00 to Maya Merchant on 11/01/2026."
        assertNull(SmsMessageParser.parse("09171234567", spoofedMaya))

        val luckyPromo = "You received PHP 5,000.00 from Lucky Winner."
        assertNull(SmsMessageParser.parse("+639991112233", luckyPromo))

        // Merchant apps removed from allowlist must be ignored
        assertFalse(SmsMessageParser.allowedNotificationPackages.contains("com.grabtaxi.passenger"))
        assertFalse(SmsMessageParser.allowedNotificationPackages.contains("com.shopee.ph"))
        assertNull(SmsMessageParser.parse("com.grabtaxi.passenger", "You paid PHP 350.00 to GrabFood."))
        assertNull(SmsMessageParser.parse("com.shopee.ph", "You paid PHP 499.00 to Shopee."))
    }

    @Test
    fun testRejectFailedDeclinedOtpPromoRequestPendingAndRefundExpense() {
        assertNull(SmsMessageParser.parse("GCash", "Your payment of PHP 500.00 to MERALCO failed due to system error."))
        assertNull(SmsMessageParser.parse("BPI", "Your transaction of PHP 1,200.00 at ZARA was declined."))
        assertNull(SmsMessageParser.parse("Maya", "Your payment of PHP 300.00 to Grab was unsuccessful."))
        assertNull(SmsMessageParser.parse("GCash", "You have insufficient balance to pay PHP 450.00 to JOLLIBEE."))
        assertNull(SmsMessageParser.parse("BDO", "Purchase of PHP 800.00 at UNIQLO was reversed."))
        assertNull(SmsMessageParser.parse("BPI", "Your scheduled payment of PHP 1,500.00 to PLDT is pending."))
        assertNull(SmsMessageParser.parse("GCash", "Never share your OTP 123456 to pay PHP 1,000.00 to Merchant."))
        assertNull(SmsMessageParser.parse("GCash", "Juan requested PHP 500.00 from you on GCash."))
        assertNull(SmsMessageParser.parse("Maya", "Promo alert! You paid PHP 100.00 to get a free voucher!"))
        // Refund must never be booked as an expense
        assertNull(SmsMessageParser.parse("BPI", "Your purchase of PHP 650.00 at LAZADA was refunded."))
    }

    @Test
    fun testShop500DoesNotMatchCurrencyRegex() {
        val msg = "You paid SHOP 500 to STORE on 11/01/2026."
        assertNull(SmsMessageParser.parse("GCash", msg))
    }

    @Test
    fun testBigDecimalAmountParsingPrecision() {
        assertEquals(1999L, SmsMessageParser.parseAmount("19.99"))
        assertEquals(115L, SmsMessageParser.parseAmount("1.15"))
        assertEquals(123456789L, SmsMessageParser.parseAmount("1,234,567.89"))
        assertEquals(0L, SmsMessageParser.parseAmount("0.00"))
    }

    @Test
    fun testDedupSameRefDifferentAccountsAndSmsNotificationCollapse() {
        val existingSms = TransactionItem(
            id = 1L,
            title = "Jollibee",
            amountCentavos = 28900L,
            type = TransactionType.EXPENSE,
            accountId = 10L,
            category = "Food & Dining",
            timestamp = 1_000_000L,
            note = "User edited note",
            sourceInfo = "From GCash message",
            refNumber = "987654321",
            dedupKey = "gcash:987654321"
        )

        // (a) Same provider + same refNumber -> duplicate even if timestamp is far apart
        val duplicateByRef = SmsMessageParser.parse(
            "GCash",
            "You have paid PHP 289.00 of GCash to JOLLIBEE on 11/01/2026. Ref. No. 987654321."
        )!!
        assertTrue(
            SmsMessageParser.isDuplicateTransaction(
                parsed = duplicateByRef,
                accountId = 10L,
                receivedAt = 5_000_000L,
                existingTransactions = listOf(existingSms)
            )
        )

        // Two same-amount purchases on different accounts -> both kept
        val secondAccountPurchase = SmsMessageParser.parse(
            "BPI",
            "Your BPI debit card ending in 7183 was charged PHP 289.00 at JOLLIBEE on 11/01/2026."
        )!!
        assertFalse(
            SmsMessageParser.isDuplicateTransaction(
                parsed = secondAccountPurchase,
                accountId = 20L,
                receivedAt = 1_010_000L,
                existingTransactions = listOf(existingSms)
            )
        )

        // (b) SMS + notification pair on same account within 90s collapses to one
        val notifNoRef = SmsMessageParser.parse(
            "com.globe.gcash.android",
            "You have paid PHP 289.00 to JOLLIBEE."
        )!!
        assertTrue(
            SmsMessageParser.isDuplicateTransaction(
                parsed = notifNoRef,
                accountId = 10L,
                receivedAt = 1_045_000L, // 45s later (< 90s) and channel = NOTIFICATION vs SMS
                existingTransactions = listOf(existingSms)
            )
        )

        // Same channel (two SMS without matching ref) does NOT collapse merely by 90s window
        val existingSmsNoRef = existingSms.copy(refNumber = null, dedupKey = null)
        val secondSmsNoRef = SmsMessageParser.parse(
            "GCash",
            "You have paid PHP 289.00 of GCash to JOLLIBEE."
        )!!
        assertFalse(
            SmsMessageParser.isDuplicateTransaction(
                parsed = secondSmsNoRef,
                accountId = 10L,
                receivedAt = 1_030_000L,
                existingTransactions = listOf(existingSmsNoRef)
            )
        )
    }

    @Test
    fun testAccountAttributionByLast4AndCreditCardBoundary() {
        val bpiSavings1111 = Account(
            id = 1L,
            name = "BPI Savings",
            provider = "bpi",
            type = AccountType.BANK,
            accountNumberMasked = "****1111",
            balanceCentavos = 500_000L,
            isSpendable = true
        )
        val bpiSavings7183 = Account(
            id = 2L,
            name = "BPI Payroll",
            provider = "bpi",
            type = AccountType.BANK,
            accountNumberMasked = "ending in 7183",
            balanceCentavos = 800_000L,
            isSpendable = true
        )

        // Matches second account by last 4 digits (7183) rather than first BPI account (1111)
        val debit7183 = SmsMessageParser.parse(
            "BPI",
            "Your BPI debit card ending in 7183 was charged PHP 500.00 at MERALCO on 11/01/2026."
        )!!
        val matched = SmsMessageParser.matchAccount(listOf(bpiSavings1111, bpiSavings7183), debit7183)
        assertEquals(2L, matched?.id)

        // Credit card message must NEVER match a savings account
        val creditMsg = SmsMessageParser.parse(
            "BPI",
            "Your BPI credit card ending in 7183 was charged PHP 1,200.00 at UNIQLO on 11/01/2026. Available credit is PHP 45,000.00."
        )!!
        assertTrue(creditMsg.isCreditCard)
        assertNull(creditMsg.newBalanceCentavos) // Available credit ignored
        val matchedForCredit = SmsMessageParser.matchAccount(listOf(bpiSavings1111, bpiSavings7183), creditMsg)
        assertNull(matchedForCredit)

        // Auto-created account for credit card is LIABILITY, non-spendable, and needsOpeningBalance = true
        val autoCreated = SmsMessageParser.buildAutoCreatedAccount(creditMsg)
        assertNotNull(autoCreated)
        assertEquals(AccountType.LIABILITY, autoCreated?.type)
        assertEquals(false, autoCreated?.isSpendable)
        assertEquals(true, autoCreated?.needsOpeningBalance)
        assertEquals("7183", autoCreated?.accountNumberMasked)
    }

    @Test
    fun testBalanceSyncOlderMessageZeroBalanceAndAdjustment() {
        val gcashAccount = Account(
            id = 5L,
            name = "GCash",
            provider = "gcash",
            type = AccountType.E_WALLET,
            accountNumberMasked = "",
            balanceCentavos = 100_000L, // ₱1,000.00
            isSpendable = true,
            balanceAsOf = 2_000L
        )

        // 1. Older message (timestamp 1,500 <= balanceAsOf 2,000) does NOT overwrite newer balance
        val olderParsed = SmsMessageParser.parse(
            "GCash",
            "You have paid PHP 100.00 of GCash to STORE. Your new balance is PHP 5,000.00. Ref. No. 11111."
        )!!
        val olderOutcome = SmsMessageParser.computeBalanceSyncOutcome(gcashAccount, olderParsed, receivedAt = 1_500L)
        assertEquals(100_000L, olderOutcome.newBalanceCentavos)
        assertEquals(2_000L, olderOutcome.newBalanceAsOf)
        assertFalse(olderOutcome.applyTxDelta)
        assertNull(olderOutcome.adjustmentTransaction)

        // 2. Reported balance of 0 is accepted, and mismatch creates an Adjustment transaction
        val zeroBalParsed = SmsMessageParser.parse(
            "GCash",
            "You have paid PHP 200.00 of GCash to STORE. Your new balance is PHP 0.00. Ref. No. 22222."
        )!!
        assertEquals(0L, zeroBalParsed.newBalanceCentavos)
        // Running balance would be 1000 - 200 = 800, but reported is 0 -> Adjustment expense of 800 (80,000 centavos)
        val mismatchOutcome = SmsMessageParser.computeBalanceSyncOutcome(gcashAccount, zeroBalParsed, receivedAt = 3_000L)
        assertEquals(0L, mismatchOutcome.newBalanceCentavos)
        assertEquals(3_000L, mismatchOutcome.newBalanceAsOf)
        assertTrue(mismatchOutcome.applyTxDelta)
        assertNotNull(mismatchOutcome.adjustmentTransaction)
        assertEquals("Adjustment", mismatchOutcome.adjustmentTransaction?.title)
        assertEquals(TransactionType.EXPENSE, mismatchOutcome.adjustmentTransaction?.type)
        assertEquals(80_000L, mismatchOutcome.adjustmentTransaction?.amountCentavos)
    }

    @Test
    fun testDetectCategoryForBill() {
        val budgets = listOf("Food & Dining", "Utilities", "Personal", "Rent & Condo")
        assertEquals("Rent & Condo", SmsMessageParser.detectCategoryForBill("Condo Rent", budgets))
        assertEquals("Personal", SmsMessageParser.detectCategoryForBill("Netflix Subscription", budgets))
        assertEquals("Utilities", SmsMessageParser.detectCategoryForBill("Meralco Electric", budgets))
    }

    @Test
    fun testDefaultCategoryIsUncategorizedAndWordBoundariesAvoidFalsePositives() {
        // Unknown merchant defaults to "Uncategorized", not "Food & Dining"
        val unknownMerchant = SmsMessageParser.parse(
            "GCash",
            "You have paid PHP 350.00 of GCash to ACME HARDWARE CORP on 11/01/2026."
        )
        assertEquals("Uncategorized", unknownMerchant?.category)

        // Substring false-positives ("GAS" in Pangasinan, "WATER" in Waterfront, "SMART" in Smartphone)
        // must not trigger Transportation or Utilities
        val pangasinan = SmsMessageParser.parse(
            "GCash",
            "You have paid PHP 500.00 of GCash to PANGASINAN TRADING on 11/01/2026."
        )
        assertEquals("Uncategorized", pangasinan?.category)

        val waterfront = SmsMessageParser.parse(
            "BPI",
            "Your BPI debit card ending in 7183 was charged PHP 3,200.00 at WATERFRONT HOTEL on 11/01/2026."
        )
        assertEquals("Uncategorized", waterfront?.category)

        val smartphone = SmsMessageParser.parse(
            "Maya",
            "You paid PHP 8,990.00 to SMARTPHONE HUB on 11/01/2026."
        )
        assertEquals("Uncategorized", smartphone?.category)

        // Whole-word matches still work
        val smartTelecom = SmsMessageParser.parse(
            "GCash",
            "You have paid PHP 499.00 of GCash to SMART COMMUNICATIONS on 11/01/2026."
        )
        assertEquals("Utilities", smartTelecom?.category)
    }
}
