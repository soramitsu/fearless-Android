package jp.co.soramitsu.common.utils

import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

private fun clickableSpan(onClick: () -> Unit) = object : ClickableSpan() {
    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
    }

    override fun onClick(widget: View) {
        onClick()
    }
}

class SpannableBuilder(val content: String) {

    private val buildingSpannable = SpannableString(content)

    fun clickable(text: String, onClick: () -> Unit) {
        val startIndex = content.indexOf(text)

        if (startIndex == -1) {
            return
        }

        val endIndex = startIndex + text.length

        buildingSpannable.setSpan(clickableSpan(onClick), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun build() = buildingSpannable
}

fun createSpannable(content: String, block: SpannableBuilder.() -> Unit): Spannable {
    val builder = SpannableBuilder(content)

    builder.block()

    return builder.build()
}
