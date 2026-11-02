package com.saannapunta.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class PesoAmountTransformationTest {

    private fun shown(raw: String) = PesoAmountTransformation.filter(AnnotatedString(raw)).text.text

    @Test
    fun groupsThousandsAndKeepsDecimals() {
        assertEquals("", shown(""))
        assertEquals("999", shown("999"))
        assertEquals("1,000", shown("1000"))
        assertEquals("12,500.5", shown("12500.5"))
        assertEquals("1,234,567.89", shown("1234567.89"))
        assertEquals("1,000.", shown("1000."))
    }

    @Test
    fun cursorMapsAcrossInsertedCommas() {
        val mapping = PesoAmountTransformation.filter(AnnotatedString("1234567")).offsetMapping
        // "1,234,567": the end of the raw text is the end of the shown text
        assertEquals(9, mapping.originalToTransformed(7))
        assertEquals(7, mapping.transformedToOriginal(9))
        // after the first digit the cursor sits past the comma
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(1, mapping.transformedToOriginal(1))
    }

    @Test
    fun leavesNonNumbersAlone() {
        assertEquals("-500", shown("-500"))
    }
}
