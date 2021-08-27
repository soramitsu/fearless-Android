package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.calculateTotalBalance
import java.math.BigInteger

class WrongNetworkTypeForSubqueryRequest(message: String) : Exception(message)

val AccountInfo.totalBalance: BigInteger
    get() = calculateTotalBalance(
        freeInPlanks = data.free,
        reservedInPlanks = data.reserved
    )

fun Node.NetworkType.getSubqueryEraValidatorInfos() =
    when (this) {
        Node.NetworkType.POLKADOT -> "fearless-wallet"
        Node.NetworkType.KUSAMA -> "fearless-wallet-ksm"
        Node.NetworkType.WESTEND -> "fearless-wallet-westend"
        else -> throw WrongNetworkTypeForSubqueryRequest("$this is not supported for fetching pending rewards via Subquery")
    }
