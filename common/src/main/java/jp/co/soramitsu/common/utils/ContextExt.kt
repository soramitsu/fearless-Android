package jp.co.soramitsu.common.utils

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

fun Context.getDrawableCompat(@DrawableRes drawableRes: Int) =
    ContextCompat.getDrawable(this, drawableRes)!!