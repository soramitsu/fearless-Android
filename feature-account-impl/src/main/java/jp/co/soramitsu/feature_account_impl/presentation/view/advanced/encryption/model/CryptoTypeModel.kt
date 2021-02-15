package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model

import jp.co.soramitsu.domain.model.CryptoType

data class CryptoTypeModel(
    val name: String,
    val cryptoType: CryptoType
)