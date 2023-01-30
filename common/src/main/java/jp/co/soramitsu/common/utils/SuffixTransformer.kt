package jp.co.soramitsu.common.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private fun getSuffixOffsetMapping(length: Int): OffsetMapping {
    return object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            return offset
        }

        override fun transformedToOriginal(offset: Int): Int {
            return (offset - length).coerceAtLeast(0)
        }
    }
}

class SuffixTransformer(
    private val suffix: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val result = text + AnnotatedString(suffix)
        return TransformedText(result, getSuffixOffsetMapping(suffix.length))
    }
}
