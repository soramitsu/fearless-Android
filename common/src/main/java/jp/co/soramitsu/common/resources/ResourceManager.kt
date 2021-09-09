package jp.co.soramitsu.common.resources

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import jp.co.soramitsu.common.R

interface ResourceManager {

    fun getString(res: Int): String

    fun getString(res: Int, vararg arguments: Any): String

    fun getColor(res: Int): Int

    fun getQuantityString(id: Int, quantity: Int): String
    fun getQuantityString(id: Int, quantity: Int, vararg arguments: Any): String

    fun measureInPx(dp: Int): Int

    fun formatDate(timestamp: Long): String

    fun formatDuration(elapsedTime: Long): String

    fun getDrawable(@DrawableRes id: Int): Drawable
}

fun ResourceManager.formatTimeLeft(elapsedTimeInMillis: Long): String {
    val durationFormatted = formatDuration(elapsedTimeInMillis)

    return getString(R.string.common_left, durationFormatted)
}
