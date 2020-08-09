package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.source.model

import jp.co.soramitsu.feature_account_api.domain.model.SourceType

data class SourceTypeModel(
    val name: String,
    val sourceType: SourceType,
    val isSelected: Boolean
)