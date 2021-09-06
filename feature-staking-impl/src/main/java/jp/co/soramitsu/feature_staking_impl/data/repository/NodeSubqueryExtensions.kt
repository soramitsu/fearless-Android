package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.core.model.Node

class WrongNetworkTypeForSubqueryRequest(message: String) : Exception(message)

fun Node.NetworkType.subqueryFearlessApiPath() =
    when (this) {
        Node.NetworkType.POLKADOT -> "fearless-wallet"
        Node.NetworkType.KUSAMA -> "fearless-wallet-ksm"
        Node.NetworkType.WESTEND -> "fearless-wallet-westend"
        else -> throw WrongNetworkTypeForSubqueryRequest("$this is not supported for fetching pending rewards via Subquery")
    }
