package jp.co.soramitsu.common.base.models

data class ErrorDialogState(
    val title: String,
    val message: String,
    val positiveButtonText: String?,
    val negativeButtonText: String?,
    val positiveClick: () -> Unit
)
