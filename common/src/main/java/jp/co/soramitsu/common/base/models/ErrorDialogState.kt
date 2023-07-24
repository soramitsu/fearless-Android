package jp.co.soramitsu.common.base.models

import android.widget.LinearLayout

data class ErrorDialogState(
    val title: String,
    val message: String,
    val positiveButtonText: String?,
    val negativeButtonText: String?,
    val buttonsOrientation: Int = LinearLayout.VERTICAL,
    val positiveClick: () -> Unit
)
