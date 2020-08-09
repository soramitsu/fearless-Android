package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.encryption.model

import jp.co.soramitsu.feature_account_api.domain.model.CryptoType

data class CryptoTypeModel(
    val name: String,
    val cryptoType: CryptoType,
    val isSelected: Boolean
)