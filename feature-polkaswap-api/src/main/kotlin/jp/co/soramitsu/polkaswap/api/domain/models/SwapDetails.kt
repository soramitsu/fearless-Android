package jp.co.soramitsu.polkaswap.api.domain.models

import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.okx.OkxBridgeInfo
import jp.co.soramitsu.common.data.network.okx.OkxDexRouter
import jp.co.soramitsu.common.data.network.okx.OkxSwapTransaction
import jp.co.soramitsu.common.data.network.okx.OkxTransactionInfo
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.core.models.Asset as ChainAsset

abstract class SwapDetails

data class PolkaswapSwapDetails(
    val amount: BigDecimal,
    val minMax: BigDecimal,
    val fromTokenOnToToken: BigDecimal,
    val toTokenOnFromToken: BigDecimal,
    val feeAsset: Asset,
    val bestDexId: Int,
    val route: String?
) : SwapDetails()

data class OkxCrossChainSwapDetailsRemote(
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val minmumReceive: String,
    val router: OkxBridgeInfo,
    val tx: OkxTransactionInfo,
)

data class OkxCrossChainSwapDetails(
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val minmumReceive: String,
    val router: OkxBridgeInfo,
    val tx: OkxTransactionInfo,
    val fromTokenOnToToken: BigDecimal,
    val toTokenOnFromToken: BigDecimal,
    val feeAsset: Asset,
) : SwapDetails()

data class OkxSwapDetails(
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val minmumReceive: String,
    val routerList: List<OkxDexRouter>,
    val tx: OkxSwapTransaction,
    val fromTokenOnToToken: BigDecimal,
    val toTokenOnFromToken: BigDecimal,
    val feeAsset: Asset,
) : SwapDetails()
