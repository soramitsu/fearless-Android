package jp.co.soramitsu.common.utils

import android.content.res.Resources
import androidx.annotation.StringRes

sealed class TextProvider {
    abstract fun getText(resources: Resources): String

    class FromResource(@StringRes private val textRes: Int) : TextProvider() {
        override fun getText(resources: Resources) = resources.getString(textRes)
    }

    class FromString(val text: String) : TextProvider() {
        override fun getText(resources: Resources) = text
    }
}

fun from(@StringRes value: Int) = TextProvider.FromResource(value)
fun from(value: String) = TextProvider.FromString(value)