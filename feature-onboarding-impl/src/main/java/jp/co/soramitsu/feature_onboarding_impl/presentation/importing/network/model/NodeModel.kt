package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.network.model

import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

data class NodeModel(
    val name: String,
    val isSelected: Boolean,
    val icon: Int,
    val link: String,
    val networkType: NetworkType
)