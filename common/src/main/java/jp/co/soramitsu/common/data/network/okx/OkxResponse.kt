package jp.co.soramitsu.common.data.network.okx

import com.google.gson.Gson

data class OkxResponse<T>(
    val code: String,
    val data: List<T>,
    val msg: String?
)

data class OkxChainItem(
    val chainId: String,
    val chainName: String,
    val dexTokenApproveAddress: String
)

data class OkxLiquidity(
    val id: String,
    val name: String
)

data class OkxApproveTransactionsResponse(
    val data: String,
    val dexContractAddress: String,
    val gasLimit: String,
    val gasPrice: String,
)

data class OkxToken(
    val decimals: String,
    val tokenContractAddress: String,
    val tokenLogoUrl: String,
    val tokenName: String,
    val tokenSymbol: String
)

data class OkxQuotesResponse(
    val chainId: String,
    val fromTokenAmount: String,    // The input amount of a token to be sold (e.g., 500000000000000000000000)
    val toTokenAmount: String,
    val estimateGasFee: String, // The recommended gas limit for calling the contract
    val fromToken: OkxDexTokenInfo,
    val toToken: OkxDexTokenInfo,
    val dexRouterList: List<OkxDexRouter>,
    val quoteCompareList: List<OkxQuoteComparison>
)

data class OkxDexRouter(
    val router: String,
    val routerPercent: String,
    val subRouterList: List<OkxDexRouterInfo>,
)

data class OkxQuoteComparison(
    val dexName: String,    // DEX name of the quote route
    val dexLogo: String,    // DEX logo of the quote route
    val tradeFee: String,   // Estimated network fee (USD) of the quote route
    val receiveAmount: String?, // Received amount of the quote route ?? TODO check amountOut
    val amountOut: String?, // Received amount of the quote route TODO check receiveAmount
)

data class OkxDexRouterInfo(
    val dexProtocol: List<OkxDexProtocol>,
    val fromToken: OkxDexTokenInfo,
    val toToken: OkxDexTokenInfo
)

data class OkxDexProtocol(
    val dexName: String,
    val percent: String,
)

data class OkxDexTokenInfo(
    val tokenContractAddress: String,
    val tokenSymbol: String,
    val tokenUnitPrice: String, // The token unit price returned by this interface is a general USD
    val decimal: String
)

data class OkxSwapResponse(
    val routerResult: OkxSwapRouterResult,
    val tx: OkxSwapTransaction
)

data class OkxSwapRouterResult(
    val chainId: String,
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val estimateGasFee: String,
    val fromToken: OkxDexTokenInfo,
    val toToken: OkxDexTokenInfo,
    val dexRouterList: List<OkxDexRouter>,
    val quoteCompareList: List<OkxQuoteComparison>
)

data class OkxSwapTransaction(
    val data: String,
    val from: String,
    val gas: String,
    val gasPrice: String,
    val maxPriorityFeePerGas: String,
    val minReceiveAmount: String,
    val to: String,
    val value: String
)

data class OkxCrossChainResponse(
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val minmumReceive: String,
    val router: OkxBridgeInfo,
    val tx: OkxTransactionInfo,
) {
    override fun toString(): String = Gson().toJson(this)
}

data class OkxBridgeInfo(
    val bridgeId: Int,
    val bridgeName: String,
    val otherNativeFee: String,
    val crossChainFee: String,
    val crossChainFeeTokenAddress: String
)

data class OkxTransactionInfo(
    val data: String,
    val from: String,
    val to: String,
    val value: String,
    val gasLimit: String,
    val gasPrice: String,
    val maxPriorityFeePerGas: String,
    val randomKeyAccount: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OkxTransactionInfo

        if (data != other.data) return false
        if (from != other.from) return false
        if (to != other.to) return false
        if (value != other.value) return false
        if (gasLimit != other.gasLimit) return false
        if (gasPrice != other.gasPrice) return false
        if (maxPriorityFeePerGas != other.maxPriorityFeePerGas) return false
        if (!randomKeyAccount.contentEquals(other.randomKeyAccount)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.hashCode()
        result = 31 * result + from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + gasLimit.hashCode()
        result = 31 * result + gasPrice.hashCode()
        result = 31 * result + maxPriorityFeePerGas.hashCode()
        result = 31 * result + randomKeyAccount.contentHashCode()
        return result
    }
}
