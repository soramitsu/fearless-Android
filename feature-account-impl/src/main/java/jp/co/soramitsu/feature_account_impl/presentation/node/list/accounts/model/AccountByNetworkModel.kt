package jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts.model

import android.graphics.drawable.PictureDrawable

data class AccountByNetworkModel(
    val nodeId: Int,
    val accountAddress: String,
    val name: String?,
    val image: PictureDrawable
)