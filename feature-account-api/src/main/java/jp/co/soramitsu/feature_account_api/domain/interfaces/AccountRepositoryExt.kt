package jp.co.soramitsu.feature_account_api.domain.interfaces

suspend fun AccountRepository.currentNetworkType() = getSelectedNode().networkType
