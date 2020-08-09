package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model

import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

data class NetworkModel(
    val name: String,
    val isSelected: Boolean,
    val icon: Int,
    val link: String,
    val networkType: NetworkType
) 