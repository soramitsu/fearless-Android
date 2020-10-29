package jp.co.soramitsu.common.utils

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat


fun Context.getDrawableCompat(@DrawableRes drawableRes: Int) =
    ContextCompat.getDrawable(this, drawableRes)!!

fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"

        putExtra(Intent.EXTRA_TEXT, text)
    }

    startActivity(intent)
}