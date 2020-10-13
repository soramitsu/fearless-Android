package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.common.utils.requirePrefix
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.Node.NetworkType.KUSAMA
import jp.co.soramitsu.feature_account_api.domain.model.Node.NetworkType.POLKADOT
import jp.co.soramitsu.feature_account_api.domain.model.Node.NetworkType.WESTEND

enum class ExternalAnalyzer(val supportedNetworks: List<Node.NetworkType>) {
    SUBSCAN(
        supportedNetworks = listOf(KUSAMA, WESTEND, POLKADOT)
    ) {
        override fun provideLink(model: TransactionModel) = with(model) {
            "https://${networkPathSegment(token.networkType)}.subscan.io/extrinsic/${hashWithPrefix(hash)}"
        }
    },

    POLKASCAN(
        supportedNetworks = listOf(KUSAMA, POLKADOT)
    ) {
        override fun provideLink(model: TransactionModel) = with(model) {
            "https://polkascan.io/${networkPathSegment(token.networkType)}/extrinsic/${hashWithPrefix(hash)}"
        }
    };

    abstract fun provideLink(model: TransactionModel): String

    fun isNetworkSupported(networkType: Node.NetworkType) = networkType in supportedNetworks
}

private fun networkPathSegment(networkType: Node.NetworkType) = networkType.readableName.toLowerCase()

private fun hashWithPrefix(hash: String) = hash.requirePrefix("0x")