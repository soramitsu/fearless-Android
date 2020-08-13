package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model

import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

data class NetworkModel(
    val name: String,
    val icon: Int,
    val smallIcon: Int,
    val link: String,
    val networkType: NetworkType,
    val isSelected: Boolean
)