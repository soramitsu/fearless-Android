package jp.co.soramitsu.account.api.presentation.accountSource

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

abstract class AccountSource(
    @StringRes val nameRes: Int,
    @StringRes val hintRes: Int,
    @DrawableRes val iconRes: Int,
    val isExport: Boolean
)
