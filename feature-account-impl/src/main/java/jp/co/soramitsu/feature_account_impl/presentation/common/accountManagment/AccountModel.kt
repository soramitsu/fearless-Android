package jp.co.soramitsu.feature_account_impl.presentation.common.accountManagment

import android.graphics.drawable.PictureDrawable

data class AccountModel(
    val address: String,
    val name: String?,
    val image: PictureDrawable
)