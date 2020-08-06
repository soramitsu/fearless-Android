package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model

import jp.co.soramitsu.feature_account_api.domain.model.EncryptionType

data class EncryptionTypeChooserDialogData(
    val selectedEncryptionType: EncryptionType,
    val encryptionTypes: List<EncryptionType>
)