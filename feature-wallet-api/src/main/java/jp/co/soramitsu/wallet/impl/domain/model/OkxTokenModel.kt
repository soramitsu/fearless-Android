package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class OkxTokenModel(
    val chainId: ChainId,
    val assetId: String?,
    val symbol: String,
    val logoUrl: String,
    val tokenName: String,
    val address: String,
    val precision: String
)