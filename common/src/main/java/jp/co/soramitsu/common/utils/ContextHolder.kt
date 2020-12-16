package jp.co.soramitsu.common.utils

import android.content.Context

interface ContextHolder {
    val providedContext: Context

    val Int.dp: Int
        get() = dp(providedContext)
}