package com.saannapunta.app.parser

import android.content.Context
import android.net.Uri
import com.saannapunta.app.data.model.Account
import com.saannapunta.app.data.model.TransactionItem

data class SmsBackfillCandidate(
    val smsId: Long,
    val sender: String,
    val timestamp: Long,
    val parsedTx: ParsedTransaction,
    val matchedAccount: Account?,
    var isSelected: Boolean = true
)

object SmsBackfillScanner {

    fun scanLast30Days(
        context: Context,
        accounts: List<Account>,
        existingTransactions: List<TransactionItem> = emptyList()
    ): List<SmsBackfillCandidate> {
        val candidates = mutableListOf<SmsBackfillCandidate>()
        val workingTransactions = existingTransactions.toMutableList()
        val uri = Uri.parse("content://sms/inbox")
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 3600 * 1000L)

        val projection = arrayOf("_id", "address", "body", "date")
        val selection = "date >= ?"
        val selectionArgs = arrayOf(thirtyDaysAgo.toString())
        val sortOrder = "date DESC"

        try {
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow("_id")
                val addressIdx = c.getColumnIndexOrThrow("address")
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val dateIdx = c.getColumnIndexOrThrow("date")

                while (c.moveToNext()) {
                    val smsId = c.getLong(idIdx)
                    val address = c.getString(addressIdx) ?: ""
                    val body = c.getString(bodyIdx) ?: ""
                    val date = c.getLong(dateIdx)

                    val parsed = SmsMessageParser.parse(address, body)
                    if (parsed != null && parsed.provider != SmsMessageParser.UNKNOWN_PROVIDER) {
                        val matchedAcc = SmsMessageParser.matchAccount(accounts, parsed)

                        val isAlreadyImported = SmsMessageParser.isDuplicateTransaction(
                            parsed = parsed,
                            accountId = matchedAcc?.id,
                            receivedAt = date,
                            existingTransactions = workingTransactions
                        )
                        if (isAlreadyImported) {
                            continue
                        }

                        candidates.add(
                            SmsBackfillCandidate(
                                smsId = smsId,
                                sender = address,
                                timestamp = date,
                                parsedTx = parsed,
                                matchedAccount = matchedAcc,
                                isSelected = true
                            )
                        )

                        workingTransactions.add(
                            TransactionItem(
                                id = -smsId,
                                title = parsed.title,
                                amountCentavos = parsed.amountCentavos,
                                type = parsed.type,
                                accountId = matchedAcc?.id ?: -1L,
                                category = parsed.category,
                                timestamp = date,
                                note = null,
                                sourceInfo = parsed.sourceInfo,
                                refNumber = parsed.refNumber,
                                dedupKey = parsed.dedupKey
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return candidates
    }
}
