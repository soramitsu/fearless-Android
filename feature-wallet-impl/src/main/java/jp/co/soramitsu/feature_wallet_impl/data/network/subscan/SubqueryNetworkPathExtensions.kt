package jp.co.soramitsu.feature_wallet_impl.data.network.subscan

import jp.co.soramitsu.core.model.Node

class WrongNetworkTypeForSubqueryRequest(message: String) : Exception(message)

fun Node.NetworkType.getSubqueryPath() =
    when (this) {
        Node.NetworkType.POLKADOT -> "fearless-wallet"
        Node.NetworkType.KUSAMA -> "fearless-wallet-ksm"
        Node.NetworkType.WESTEND -> "fearless-wallet-westend"
        else -> throw WrongNetworkTypeForSubqueryRequest("$this is not supported for fetching pending rewards via Subquery")
    }
