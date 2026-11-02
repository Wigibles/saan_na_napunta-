package com.saannapunta.app.ui.main

import com.saannapunta.app.data.model.MoneyFormatter
import com.saannapunta.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun testFormatting() {
        assertEquals("₱14,850.50", MoneyFormatter.format(1485050L))
        assertEquals("₱48,200", MoneyFormatter.format(4820000L, includeDecimals = false))
        assertEquals("−₱289.00", MoneyFormatter.formatWithSign(28900L, TransactionType.EXPENSE))
        assertEquals("+₱24,000.00", MoneyFormatter.formatWithSign(2400000L, TransactionType.INCOME))
    }
}
