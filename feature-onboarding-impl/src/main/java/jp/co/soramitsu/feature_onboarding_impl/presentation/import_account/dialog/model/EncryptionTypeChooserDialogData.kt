package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model

import jp.co.soramitsu.feature_account_api.domain.model.CryptoType

data class EncryptionTypeChooserDialogData(
    val selectedEncryptionType: CryptoType,
    val encryptionTypes: List<CryptoType>
)