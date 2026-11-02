package com.saannapunta.app.parser

import com.saannapunta.app.data.ProviderCatalog
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.AccountType
import com.saannapunta.app.data.model.TransactionItem
import com.saannapunta.app.data.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.regex.Pattern
import kotlin.math.abs

enum class IngestionChannel {
    SMS,
    NOTIFICATION,
    MANUAL
}

data class ProviderAllowlistEntry(
    val displayName: String,
    val smsSenders: Set<String>,
    val notificationPackages: Set<String>
)

data class ParsedTransaction(
    val title: String,
    val amountCentavos: Long,
    val type: TransactionType,
    val provider: String,
    val category: String,
    val sourceInfo: String,
    val rawMessage: String,
    val newBalanceCentavos: Long? = null,
    val refNumber: String? = null,
    val dedupKey: String? = if (!refNumber.isNullOrBlank()) "${provider.lowercase()}:${refNumber.uppercase()}" else null,
    val accountLast4: String? = null,
    val isCreditCard: Boolean = false,
    val channel: IngestionChannel = IngestionChannel.SMS
)

sealed class ParseOutcome {
    data class Success(val transaction: ParsedTransaction) : ParseOutcome()
    data class Ignored(val reason: String) : ParseOutcome()
}

data class BalanceSyncOutcome(
    val newBalanceCentavos: Long,
    val newBalanceAsOf: Long,
    val applyTxDelta: Boolean,
    val adjustmentTransaction: TransactionItem?
)

object SmsMessageParser {

    const val UNKNOWN_PROVIDER = "bank"

    /**
     * Single source-of-truth allowlist mapping each provider key to its recognized SMS sender IDs /
     * shortcodes and Android notification package names. Merchant apps (Grab, Shopee) are excluded
     * because they duplicate the payment provider's own notification/SMS.
     */
    val PROVIDER_ALLOWLIST: Map<String, ProviderAllowlistEntry> = mapOf(
        "gcash" to ProviderAllowlistEntry(
            displayName = "GCash",
            smsSenders = setOf("gcash", "2882"),
            notificationPackages = setOf("com.globe.gcash.android")
        ),
        "maya" to ProviderAllowlistEntry(
            displayName = "Maya",
            smsSenders = setOf("maya", "paymaya"),
            notificationPackages = setOf("com.paymaya")
        ),
        "gotyme" to ProviderAllowlistEntry(
            displayName = "GoTyme",
            smsSenders = setOf("gotyme", "gotymebank"),
            notificationPackages = setOf("ph.gotyme.app")
        ),
        "bpi" to ProviderAllowlistEntry(
            displayName = "BPI",
            smsSenders = setOf("bpi"),
            notificationPackages = setOf("com.bpi.ng.app")
        ),
        "vybe" to ProviderAllowlistEntry(
            displayName = "Vybe",
            smsSenders = setOf("vybe", "bpivybe"),
            notificationPackages = setOf("com.bpi.vybe")
        ),
        "bdo" to ProviderAllowlistEntry(
            displayName = "BDO",
            smsSenders = setOf("bdo", "bdo-alert", "bdoalert"),
            notificationPackages = setOf("ph.com.bdo.retail")
        ),
        "unionbank" to ProviderAllowlistEntry(
            displayName = "UnionBank",
            smsSenders = setOf("unionbank", "ubp"),
            notificationPackages = setOf("com.unionbankph.online")
        ),
        "uniondigital" to ProviderAllowlistEntry(
            displayName = "UnionDigital",
            smsSenders = setOf("uniondigital", "udbank"),
            notificationPackages = setOf("ph.uniondigitalbank.app")
        ),
        "maribank" to ProviderAllowlistEntry(
            displayName = "MariBank",
            smsSenders = setOf("seabank", "maribank"),
            notificationPackages = setOf("ph.seabank.seabank")
        ),
        "cimb" to ProviderAllowlistEntry(
            displayName = "CIMB",
            smsSenders = setOf("cimb", "cimbbank", "cimbph"),
            notificationPackages = setOf("com.cimb.octoph")
        ),
        "tonik" to ProviderAllowlistEntry(
            displayName = "Tonik",
            smsSenders = setOf("tonik", "tonikbank"),
            notificationPackages = setOf("com.tonik.mobile")
        ),
        "unobank" to ProviderAllowlistEntry(
            displayName = "UNO Digital Bank",
            smsSenders = setOf("unobank", "unodigital"),
            notificationPackages = setOf("ph.unobank.app")
        ),
        "metrobank" to ProviderAllowlistEntry(
            displayName = "Metrobank",
            smsSenders = setOf("metrobank", "mbtc"),
            notificationPackages = setOf("com.metrobank.mcc.mbanking")
        ),
        "rcbc" to ProviderAllowlistEntry(
            displayName = "RCBC",
            smsSenders = setOf("rcbc", "rcbcpulz"),
            notificationPackages = setOf("com.rcbc.pulz")
        ),
        "securitybank" to ProviderAllowlistEntry(
            displayName = "Security Bank",
            smsSenders = setOf("securitybank", "secbank", "security bank"),
            notificationPackages = setOf("com.securitybank.bbx")
        ),
        "landbank" to ProviderAllowlistEntry(
            displayName = "Landbank",
            smsSenders = setOf("landbank", "lbp"),
            notificationPackages = emptySet()
        ),
        "chinabank" to ProviderAllowlistEntry(
            displayName = "China Bank",
            smsSenders = setOf("chinabank", "china bank", "cbc"),
            notificationPackages = setOf("com.chinabank.mycbc")
        ),
        "eastwest" to ProviderAllowlistEntry(
            displayName = "EastWest",
            smsSenders = setOf("eastwest", "ewb", "eastwestbank"),
            notificationPackages = setOf("com.eastwest.easyway")
        ),
        "psbank" to ProviderAllowlistEntry(
            displayName = "PSBank",
            smsSenders = setOf("psbank"),
            notificationPackages = setOf("com.psbank.mobile")
        ),
        "pnb" to ProviderAllowlistEntry(
            displayName = "PNB",
            smsSenders = setOf("pnb"),
            notificationPackages = emptySet()
        ),
        "coinsph" to ProviderAllowlistEntry(
            displayName = "Coins.ph",
            smsSenders = setOf("coins.ph", "coinsph"),
            notificationPackages = setOf("asia.coins.mobile")
        ),
        "palawanpay" to ProviderAllowlistEntry(
            displayName = "Palawan Pay",
            smsSenders = setOf("palawanpay", "palawan"),
            notificationPackages = setOf("com.palawanpay.ewallet")
        )
    )

    val allowedNotificationPackages: Set<String> =
        PROVIDER_ALLOWLIST.values.flatMap { it.notificationPackages }.toSet()

    // Word boundary \b before PHP, Php, and P prevents matching words ending in P like "SHOP 500".
    private const val CURRENCY = """(?:₱|\b(?:PHP|Php|P))\s*([\d,]+(?:\.\d+)?)"""

    private val GCASH_PAID_REGEX = Pattern.compile(
        """(?:You have paid|paid)\s+$CURRENCY\s*(?:of GCash\s+)?to\s+([^.\n]+?)(?:\s+on|\.|$|\s+with\s+ref|\s+Your|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val GCASH_SENT_REGEX = Pattern.compile(
        """(?:You have sent|sent)\s+$CURRENCY\s*(?:of GCash\s+)?to\s+([^.\n]+?)(?:\s+on|\.|$|\s+with\s+ref|\s+Your|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val GCASH_RECEIVED_REGEX = Pattern.compile(
        """(?:You have received|received)\s+$CURRENCY\s*(?:of GCash\s+)?from\s+([^.\n]+?)(?:\s+on|\.|$|\s+with\s+ref|\s+Your|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val MAYA_PAID_REGEX = Pattern.compile(
        """(?:You paid|paid)\s+$CURRENCY\s*to\s+([^.\n]+?)(?:\s+on|\.|$|\s+with\s+ref|\s+Reference|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val MAYA_SENT_REGEX = Pattern.compile(
        """(?:You sent|sent)\s+$CURRENCY\s*to\s+([^.\n]+?)(?:\s+on|\.|$|\s+with\s+ref|\s+Reference|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val MAYA_RECEIVED_REGEX = Pattern.compile(
        """(?:You received|received)\s+$CURRENCY\s*from\s+([^.\n]+?)(?:\s+on|\.|$|\s+with\s+ref|\s+Reference|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val BANK_CHARGED_REGEX = Pattern.compile(
        """(?:Purchase of|Purchase|Transaction of|Payment of|charged|debited for|debited|spent|paid|withdrawn)\s+$CURRENCY\s*(?:at|to)\s+([^.\n]+?)(?:\s+on|\.|$|\s+using|\s+with|\s+was|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val BANK_CARD_REGEX = Pattern.compile(
        """(?:card ending in \d+ for|used your card .*? for)\s+$CURRENCY\s*(?:at|to)\s+([^.\n]+?)(?:\s+on|\.|$|\s+using|\s+with|\s+was|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val ATM_WITHDRAWAL_REGEX = Pattern.compile(
        """(?:ATM withdrawal of|withdrawal of)\s+$CURRENCY(?:\s+(?:from|at)\s+([^.\n]+?))?(?:\s+on|\.|$|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val BANK_CREDITED_REGEX = Pattern.compile(
        """(?:credited with|received|deposited)\s+$CURRENCY\s*(?:from\s+([^.\n]+?))?(?:\s+on|\.|$|\s+Ref)""",
        Pattern.CASE_INSENSITIVE
    )

    private val BALANCE_REGEX = Pattern.compile(
        """\b(?:new\s+balance\s+is|available\s+balance\s+is|avail\.?\s+bal(?:ance)?\s+is|balance\s+is)\s*$CURRENCY""",
        Pattern.CASE_INSENSITIVE
    )

    private val REF_NUMBER_REGEX = Pattern.compile(
        """\b(?:ref(?:erence)?\.?\s*(?:no\.?|number|id|#|:)|ref\.?\s+|trace\s*(?:no\.?|#|:)|txn\s*ref\.?\s*:?)\s*[:#.]?\s*([A-Za-z0-9-]{4,})\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val ACCOUNT_LAST4_REGEX = Pattern.compile(
        """(?:(?:card|account|acct)\s+(?:ending\s+(?:in\s+)?|no\.?\s*|\*+|x+)?|\bending\s+(?:in\s+)?|(?:\*{2,}|x{2,})\s*)(\d{4})\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_CARD_REGEX = Pattern.compile(
        """\b(?:credit\s+card|cc\s+ending|avail(?:able)?\s+credit|credit\s+limit|(?:visa|mastercard|jcb|amex)\s+credit)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val REJECT_FAILED_REGEX = Pattern.compile(
        """\b(?:failed|declined|unsuccessful|insufficient|reversed|unable\s+to\s+process|did\s+not\s+push\s+through)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val REJECT_PENDING_REGEX = Pattern.compile(
        """\b(?:pending|scheduled)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val REJECT_OTP_REGEX = Pattern.compile(
        """\b(?:otp|one-time\s+pin|one-time\s+password|verification\s+code|security\s+code|auth\s+code|never\s+share\s+your)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val REJECT_REQUEST_REGEX = Pattern.compile(
        """\brequest(?:ed|ing|s)?\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val REJECT_PROMO_REGEX = Pattern.compile(
        """\b(?:promo|promos|promotion|cashback\s+voucher|voucher|freebie|raffle|win\s+up\s+to|earn\s+points|get\s+up\s+to|enjoy\s+up\s+to|discount|avail\s+now)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val REFUND_REGEX = Pattern.compile(
        """\brefund(?:ed)?\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_CARD_PAYMENT_MERCHANT_REGEX = Pattern.compile(
        """\b(?:credit\s+card|cc\s+payment|cc\s+bill|(?:bpi|bdo|metrobank|rcbc|citi|citibank|eastwest|unionbank|security\s+bank|chinabank|hsbc|pnb)\s+(?:credit\s+)?card(?:\s+bill)?)\b""",
        Pattern.CASE_INSENSITIVE
    )

    internal fun isCreditCardPaymentMerchant(titleOrMerchant: String): Boolean {
        return CREDIT_CARD_PAYMENT_MERCHANT_REGEX.matcher(titleOrMerchant).find()
    }

    private data class ResolvedSender(
        val providerKey: String,
        val entry: ProviderAllowlistEntry,
        val channel: IngestionChannel
    )

    private fun resolveSender(senderOrPackage: String?): ResolvedSender? {
        val normalized = senderOrPackage?.trim()?.lowercase() ?: return null
        if (normalized.isEmpty()) return null

        for ((key, entry) in PROVIDER_ALLOWLIST) {
            if (normalized in entry.notificationPackages) {
                return ResolvedSender(key, entry, IngestionChannel.NOTIFICATION)
            }
            if (normalized in entry.smsSenders) {
                return ResolvedSender(key, entry, IngestionChannel.SMS)
            }
        }
        return null
    }

    fun parse(senderOrPackage: String?, message: String): ParsedTransaction? {
        return (parseDetailed(senderOrPackage, message) as? ParseOutcome.Success)?.transaction
    }

    fun parseDetailed(senderOrPackage: String?, message: String): ParseOutcome {
        val m = message.trim()
        if (m.isEmpty()) return ParseOutcome.Ignored("Empty message")

        val resolved = resolveSender(senderOrPackage)
            ?: return ParseOutcome.Ignored("Sender not in allowlist")

        // Reject non-transactions before matching amounts
        if (REJECT_FAILED_REGEX.matcher(m).find()) {
            return ParseOutcome.Ignored("Rejected: failed/declined/insufficient/reversed wording")
        }
        if (REJECT_PENDING_REGEX.matcher(m).find()) {
            return ParseOutcome.Ignored("Rejected: pending/scheduled wording")
        }
        if (REJECT_OTP_REGEX.matcher(m).find()) {
            return ParseOutcome.Ignored("Rejected: OTP/verification wording")
        }
        if (REJECT_REQUEST_REGEX.matcher(m).find()) {
            return ParseOutcome.Ignored("Rejected: request wording")
        }
        if (REJECT_PROMO_REGEX.matcher(m).find()) {
            return ParseOutcome.Ignored("Rejected: promo wording")
        }

        val isRefund = REFUND_REGEX.matcher(m).find()
        val provider = resolved.providerKey
        val displayProvider = resolved.entry.displayName
        val channel = resolved.channel
        val sourceLabel = if (channel == IngestionChannel.NOTIFICATION) {
            "From $displayProvider notification"
        } else {
            "From $displayProvider message"
        }

        val refNum = extractRefNumber(m)
        val last4 = extractAccountLast4(m)
        val isCredit = detectIsCreditCard(m)
        val reportedBalance = if (isCredit) null else parseBalance(m)

        fun buildTx(
            title: String,
            amountCentavos: Long,
            type: TransactionType,
            category: String
        ): ParseOutcome {
            if (isRefund && type == TransactionType.EXPENSE) {
                return ParseOutcome.Ignored("Rejected: refund cannot be booked as expense")
            }
            val isCcPayment = type == TransactionType.EXPENSE && isCreditCardPaymentMerchant(title)
            val finalType = if (isCcPayment) TransactionType.TRANSFER else type
            val finalCategory = if (isCcPayment) "Credit Card Payment" else category
            val finalIsCredit = if (isCcPayment) false else isCredit
            val finalBalance = if (finalIsCredit) null else parseBalance(m)
            return ParseOutcome.Success(
                ParsedTransaction(
                    title = title,
                    amountCentavos = amountCentavos,
                    type = finalType,
                    provider = provider,
                    category = finalCategory,
                    sourceInfo = sourceLabel,
                    rawMessage = m,
                    newBalanceCentavos = finalBalance,
                    refNumber = refNum,
                    accountLast4 = last4,
                    isCreditCard = finalIsCredit,
                    channel = channel
                )
            )
        }

        // 1. GCash specific patterns
        if (provider == "gcash") {
            var matcher = GCASH_PAID_REGEX.matcher(m)
            if (matcher.find()) {
                val amt = parseAmount(matcher.group(1))
                if (amt > 0) {
                    val merchant = cleanMerchant(matcher.group(2))
                    return buildTx(merchant, amt, TransactionType.EXPENSE, detectCategory(merchant))
                }
            }

            matcher = GCASH_SENT_REGEX.matcher(m)
            if (matcher.find()) {
                val amt = parseAmount(matcher.group(1))
                if (amt > 0) {
                    val target = cleanMerchant(matcher.group(2))
                    return buildTx("Send to $target", amt, TransactionType.EXPENSE, "Personal")
                }
            }

            matcher = GCASH_RECEIVED_REGEX.matcher(m)
            if (matcher.find()) {
                val amt = parseAmount(matcher.group(1))
                if (amt > 0) {
                    val from = cleanMerchant(matcher.group(2))
                    return buildTx("Received from $from", amt, TransactionType.INCOME, "Income")
                }
            }
        }

        // 2. Maya specific patterns
        if (provider == "maya") {
            var matcher = MAYA_PAID_REGEX.matcher(m)
            if (matcher.find()) {
                val amt = parseAmount(matcher.group(1))
                if (amt > 0) {
                    val merchant = cleanMerchant(matcher.group(2))
                    return buildTx(merchant, amt, TransactionType.EXPENSE, detectCategory(merchant))
                }
            }

            matcher = MAYA_SENT_REGEX.matcher(m)
            if (matcher.find()) {
                val amt = parseAmount(matcher.group(1))
                if (amt > 0) {
                    val target = cleanMerchant(matcher.group(2))
                    return buildTx("Send to $target", amt, TransactionType.EXPENSE, "Personal")
                }
            }

            matcher = MAYA_RECEIVED_REGEX.matcher(m)
            if (matcher.find()) {
                val amt = parseAmount(matcher.group(1))
                if (amt > 0) {
                    val from = cleanMerchant(matcher.group(2))
                    return buildTx("Received from $from", amt, TransactionType.INCOME, "Income")
                }
            }
        }

        // 3. Generic bank & e-wallet transaction patterns
        var bankMatcher = BANK_CHARGED_REGEX.matcher(m)
        if (bankMatcher.find()) {
            val amt = parseAmount(bankMatcher.group(1))
            if (amt > 0) {
                val merchant = cleanMerchant(bankMatcher.group(2))
                return buildTx(merchant, amt, TransactionType.EXPENSE, detectCategory(merchant))
            }
        }

        bankMatcher = BANK_CARD_REGEX.matcher(m)
        if (bankMatcher.find()) {
            val amt = parseAmount(bankMatcher.group(1))
            if (amt > 0) {
                val merchant = cleanMerchant(bankMatcher.group(2))
                return buildTx(merchant, amt, TransactionType.EXPENSE, detectCategory(merchant))
            }
        }

        val atmMatcher = ATM_WITHDRAWAL_REGEX.matcher(m)
        if (atmMatcher.find()) {
            val amt = parseAmount(atmMatcher.group(1))
            if (amt > 0) {
                val location = atmMatcher.group(2)?.let { " (${cleanMerchant(it)})" } ?: ""
                return buildTx("ATM Withdrawal$location", amt, TransactionType.TRANSFER, "Transfer")
            }
        }

        val creditMatcher = BANK_CREDITED_REGEX.matcher(m)
        if (creditMatcher.find()) {
            val amt = parseAmount(creditMatcher.group(1))
            if (amt > 0) {
                val senderName = creditMatcher.group(2)?.let { "Received from ${cleanMerchant(it)}" } ?: "Deposit / Credit"
                return buildTx(senderName, amt, TransactionType.INCOME, "Income")
            }
        }

        if (isRefund) {
            return ParseOutcome.Ignored("Rejected: refund message")
        }

        return ParseOutcome.Ignored("Unrecognized message format")
    }

    internal fun parseAmountOrNull(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val clean = str.replace(",", "").trim().trimEnd('.')
        if (clean.isEmpty()) return null
        return try {
            BigDecimal(clean)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseAmount(str: String?): Long = parseAmountOrNull(str) ?: 0L

    internal fun parseBalance(message: String): Long? {
        if (detectIsCreditCard(message)) return null
        val m = BALANCE_REGEX.matcher(message)
        if (m.find()) {
            val amt = parseAmountOrNull(m.group(1))
            return if (amt != null && amt >= 0L) amt else null
        }
        return null
    }

    internal fun extractRefNumber(message: String): String? {
        val m = REF_NUMBER_REGEX.matcher(message)
        if (m.find()) {
            val raw = m.group(1)?.trim('.', ',', ';', ':') ?: return null
            if (raw.length >= 4 && raw.any { it.isDigit() }) {
                return raw
            }
        }
        return null
    }

    internal fun extractAccountLast4(message: String): String? {
        val m = ACCOUNT_LAST4_REGEX.matcher(message)
        return if (m.find()) m.group(1) else null
    }

    internal fun detectIsCreditCard(message: String): Boolean {
        return CREDIT_CARD_REGEX.matcher(message).find()
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.trim().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun channelFromSourceInfo(sourceInfo: String): IngestionChannel {
        val lower = sourceInfo.lowercase()
        return when {
            lower.contains("notification") -> IngestionChannel.NOTIFICATION
            lower.contains("message") || lower.contains("sms") -> IngestionChannel.SMS
            else -> IngestionChannel.MANUAL
        }
    }

    /**
     * Duplicate rules:
     * (a) Same provider + same refNumber (via dedupKey = "provider:REF"), OR
     * (b) Same account + amount + type within 90 seconds AND the sources differ (SMS vs notification).
     * Never inspects the user-editable note field.
     */
    fun isDuplicateTransaction(
        parsed: ParsedTransaction,
        accountId: Long?,
        receivedAt: Long,
        existingTransactions: List<TransactionItem>
    ): Boolean {
        val candidateKey = parsed.dedupKey
        if (!candidateKey.isNullOrBlank()) {
            val refMatch = existingTransactions.any { existing ->
                !existing.dedupKey.isNullOrBlank() && existing.dedupKey.equals(candidateKey, ignoreCase = true)
            }
            if (refMatch) return true
        }

        if (accountId != null && (parsed.channel == IngestionChannel.SMS || parsed.channel == IngestionChannel.NOTIFICATION)) {
            val crossChannelMatch = existingTransactions.any { existing ->
                val existingChannel = channelFromSourceInfo(existing.sourceInfo)
                val sourcesDiffer = (existingChannel == IngestionChannel.SMS && parsed.channel == IngestionChannel.NOTIFICATION) ||
                    (existingChannel == IngestionChannel.NOTIFICATION && parsed.channel == IngestionChannel.SMS)
                val refsCompatible = existing.refNumber.isNullOrBlank() ||
                    parsed.refNumber.isNullOrBlank() ||
                    existing.refNumber.equals(parsed.refNumber, ignoreCase = true)

                sourcesDiffer &&
                    refsCompatible &&
                    existing.accountId == accountId &&
                    existing.amountCentavos == parsed.amountCentavos &&
                    existing.type == parsed.type &&
                    abs(existing.timestamp - receivedAt) <= 90_000L
            }
            if (crossChannelMatch) return true
        }

        return false
    }

    /**
     * Matches a parsed message to an account:
     * 1. Enforces credit card (LIABILITY) vs non-credit card boundary so a credit card message is
     *    never booked to a savings/debit/wallet account (and vice versa).
     * 2. Matches by last 4 digits in accountNumberMasked before falling back to provider.
     */
    fun matchAccount(accounts: List<Account>, parsed: ParsedTransaction): Account? {
        val activeCompatible = accounts.filter { acc ->
            acc.archivedAt == null &&
                (if (parsed.isCreditCard) acc.type == AccountType.LIABILITY else acc.type != AccountType.LIABILITY)
        }

        val last4 = parsed.accountLast4
        if (!last4.isNullOrBlank()) {
            // 1a. Exact last-4 + provider match
            activeCompatible.firstOrNull { acc ->
                extractLast4FromMask(acc.accountNumberMasked) == last4 &&
                    acc.provider.equals(parsed.provider, ignoreCase = true)
            }?.let { return it }

            // 1b. Exact last-4 match across compatible accounts
            activeCompatible.firstOrNull { acc ->
                extractLast4FromMask(acc.accountNumberMasked) == last4
            }?.let { return it }

            // 1c. Fallback to provider only if the candidate account has no conflicting masked digits
            return activeCompatible.firstOrNull { acc ->
                extractLast4FromMask(acc.accountNumberMasked) == null &&
                    (acc.provider.equals(parsed.provider, ignoreCase = true) || acc.name.contains(parsed.provider, ignoreCase = true))
            }
        }

        return activeCompatible.firstOrNull { acc ->
            acc.provider.equals(parsed.provider, ignoreCase = true)
        } ?: activeCompatible.firstOrNull { acc ->
            acc.name.contains(parsed.provider, ignoreCase = true)
        }
    }

    internal fun extractLast4FromMask(masked: String): String? {
        val digits = masked.filter { it.isDigit() }
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    fun buildAutoCreatedAccount(parsed: ParsedTransaction): Account? {
        val info = ProviderCatalog.all.firstOrNull { it.provider.equals(parsed.provider, ignoreCase = true) }
        val displayName = info?.name ?: PROVIDER_ALLOWLIST[parsed.provider]?.displayName ?: return null
        val type = if (parsed.isCreditCard) AccountType.LIABILITY else (info?.type ?: AccountType.BANK)
        val name = if (parsed.isCreditCard) "$displayName Credit Card" else displayName
        return Account(
            id = 0L,
            name = name,
            provider = parsed.provider,
            type = type,
            accountNumberMasked = parsed.accountLast4 ?: "",
            balanceCentavos = 0L,
            isSpendable = !parsed.isCreditCard,
            statusLine = "Needs opening balance",
            iconResName = info?.iconResName,
            balanceAsOf = 0L,
            needsOpeningBalance = true,
            archivedAt = null
        )
    }

    /**
     * Computes balance sync outcome for an account when recording an auto-ingested transaction.
     * - If message timestamp <= account.balanceAsOf (and balanceAsOf > 0), the older message does
     *   not overwrite or mutate the newer balance.
     * - Liability accounts never apply reported "available credit" balances.
     * - When a newer reported balance differs from the running total after applying the transaction,
     *   an "Adjustment" transaction is produced for the difference instead of silently overwriting.
     */
    fun computeBalanceSyncOutcome(
        account: Account,
        parsed: ParsedTransaction,
        receivedAt: Long
    ): BalanceSyncOutcome {
        if (account.balanceAsOf > 0L && receivedAt <= account.balanceAsOf) {
            return BalanceSyncOutcome(
                newBalanceCentavos = account.balanceCentavos,
                newBalanceAsOf = account.balanceAsOf,
                applyTxDelta = false,
                adjustmentTransaction = null
            )
        }

        val moneyIn = when (parsed.type) {
            TransactionType.EXPENSE -> -parsed.amountCentavos
            TransactionType.INCOME -> parsed.amountCentavos
            TransactionType.TRANSFER -> -parsed.amountCentavos
        }
        val storedDelta = if (account.type == AccountType.LIABILITY) -moneyIn else moneyIn
        val runningBalance = account.balanceCentavos + storedDelta

        val reported = if (account.type == AccountType.LIABILITY || parsed.isCreditCard) {
            null
        } else {
            parsed.newBalanceCentavos
        }

        if (reported != null) {
            val diff = reported - runningBalance
            val adjustmentTx = if (diff != 0L) {
                TransactionItem(
                    title = "Adjustment",
                    amountCentavos = abs(diff),
                    type = if (diff > 0L) TransactionType.INCOME else TransactionType.EXPENSE,
                    accountId = account.id,
                    category = "Adjustment",
                    timestamp = receivedAt,
                    note = "Balance sync adjustment",
                    sourceInfo = "Balance sync"
                )
            } else null

            return BalanceSyncOutcome(
                newBalanceCentavos = reported,
                newBalanceAsOf = receivedAt,
                applyTxDelta = true,
                adjustmentTransaction = adjustmentTx
            )
        }

        return BalanceSyncOutcome(
            newBalanceCentavos = runningBalance,
            newBalanceAsOf = account.balanceAsOf,
            applyTxDelta = true,
            adjustmentTransaction = null
        )
    }

    private fun cleanMerchant(str: String?): String {
        if (str == null) return "Merchant"
        val clean = str.trim()
            .replace(Regex("""\s+(?:with\s+ref(?:erence)?|with|ref|reference).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\d{10,12}$"""), "")
            .trim()
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        return clean.ifBlank { "Merchant" }
    }

    fun detectCategoryForBill(billTitle: String, userBudgetCategories: List<String> = emptyList()): String {
        val u = billTitle.uppercase()
        userBudgetCategories.firstOrNull { u.contains(it.uppercase()) }?.let { return it }
        return when {
            u.contains("RENT") || u.contains("CONDO") || u.contains("ASSOC") || u.contains("MORTGAGE") -> {
                userBudgetCategories.firstOrNull { it.contains("Rent", ignoreCase = true) || it.contains("Housing", ignoreCase = true) }
                    ?: "Utilities"
            }
            u.contains("NETFLIX") || u.contains("SPOTIFY") || u.contains("YOUTUBE") ||
                u.contains("DISNEY") || u.contains("APPLE") || u.contains("GOOGLE") -> {
                userBudgetCategories.firstOrNull { it.contains("Personal", ignoreCase = true) || it.contains("Subscription", ignoreCase = true) }
                    ?: "Personal"
            }
            else -> "Utilities"
        }
    }

    private val UTILITIES_CATEGORY_REGEX = Pattern.compile(
        """\b(?:MERALCO|MAYNILAD|MANILA\s+WATER|PRIMEWATER|WATER|CONVERGE|PLDT|GLOBE|SMART|DITO|ELECTRIC|VECO|CEBECO|ANECO|BENECO)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val TRANSPORT_CATEGORY_REGEX = Pattern.compile(
        """\b(?:GRAB|SHELL|PETRON|CALTEX|SEAOIL|PHOENIX|CLEANFUEL|UNIOIL|ANGKAS|JOYRIDE|MOVE\s+IT|LRT|MRT|BEEP|BEEPTM|AUTOSWEEP|EASYTRIP|TAXI|GAS)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val PERSONAL_CATEGORY_REGEX = Pattern.compile(
        """\b(?:UNIQLO|ZARA|WATSONS|MERCURY|SHOPEE|LAZADA|TIKTOK|MINISO|DAISO|SM\s+DEPT|BENCH|PENSHOPPE)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val FOOD_CATEGORY_REGEX = Pattern.compile(
        """\b(?:JOLLIBEE|MCDO|MCDONALD'?S|KFC|CHOWKING|MANG\s+INASAL|STARBUCKS|GREENWICH|RED\s+RIBBON|GOLDILOCKS|SHAKEY'?S|PIZZA\s+HUT|FOODPANDA|GRABFOOD|FOOD|RESTAURANT|BURGER|PIZZA|PUREGOLD|SUPERMARKET|SM\s+HYPERMARKET|ROBINSONS|LANDERS|S&R|COFFEE|BAKERY|GROCERY|7-ELEVEN|ALFAMART|DALI|LAWSON|MINISTOP|UNCLE\s+JOHN'?S)\b""",
        Pattern.CASE_INSENSITIVE
    )

    internal fun detectCategory(merchant: String): String {
        val m = merchant.trim()
        return when {
            UTILITIES_CATEGORY_REGEX.matcher(m).find() -> "Utilities"
            TRANSPORT_CATEGORY_REGEX.matcher(m).find() -> "Transportation"
            PERSONAL_CATEGORY_REGEX.matcher(m).find() -> "Personal"
            FOOD_CATEGORY_REGEX.matcher(m).find() -> "Food & Dining"
            else -> "Uncategorized"
        }
    }
}
