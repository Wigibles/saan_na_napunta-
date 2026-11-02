package com.saannapunta.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows a raw amount like "12500.5" as "12,500.5" while the field's value stays plain digits, so
 * existing parsing is untouched. Anything that isn't a plain number is shown as typed.
 */
object PesoAmountTransformation : VisualTransformation {
    private val PLAIN_NUMBER = Regex("""\d*(\.\d*)?""")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!PLAIN_NUMBER.matches(raw)) return TransformedText(text, OffsetMapping.Identity)

        val dot = raw.indexOf('.').let { if (it < 0) raw.length else it }
        val out = StringBuilder()
        val originalToTransformed = IntArray(raw.length + 1)
        for (i in 0 until dot) {
            if (i > 0 && (dot - i) % 3 == 0) out.append(',')
            originalToTransformed[i] = out.length
            out.append(raw[i])
        }
        for (i in dot until raw.length) {
            originalToTransformed[i] = out.length
            out.append(raw[i])
        }
        originalToTransformed[raw.length] = out.length

        val transformedToOriginal = IntArray(out.length + 1)
        var original = 0
        for (t in 0..out.length) {
            transformedToOriginal[t] = original
            if (t < out.length && out[t] != ',') original++
        }

        return TransformedText(
            AnnotatedString(out.toString()),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = originalToTransformed[offset.coerceIn(0, raw.length)]
                override fun transformedToOriginal(offset: Int) = transformedToOriginal[offset.coerceIn(0, out.length)]
            }
        )
    }
}
