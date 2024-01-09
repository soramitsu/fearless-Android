package jp.co.soramitsu.common.base.models

import android.widget.LinearLayout
import jp.co.soramitsu.common.compose.component.emptyClick

data class ErrorDialogState(
    val title: String?,
    val message: String,
    val positiveButtonText: String?,
    val negativeButtonText: String?,
    val buttonsOrientation: Int = LinearLayout.VERTICAL,
    val positiveClick: () -> Unit,
    val negativeClick: () -> Unit = emptyClick,
    val onBackClick: () -> Unit = emptyClick,
    val isHideable: Boolean = true
)
