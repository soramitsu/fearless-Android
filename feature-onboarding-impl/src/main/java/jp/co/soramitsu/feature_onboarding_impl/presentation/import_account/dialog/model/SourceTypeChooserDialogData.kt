package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model

import jp.co.soramitsu.feature_account_api.domain.model.SourceType

data class SourceTypeChooserDialogData(
    val selectedSourceType: SourceType,
    val sourceTypes: List<SourceType>
)