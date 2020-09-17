package jp.co.soramitsu.feature_account_impl.presentation.account.model

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network

data class AccountModel(
    val address: String,
    val name: String?,
    val image: PictureDrawable,
    val publicKey: String,
    val cryptoType: CryptoType,
    val network: Network
)