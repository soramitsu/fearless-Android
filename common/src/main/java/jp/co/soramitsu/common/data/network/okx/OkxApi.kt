package jp.co.soramitsu.common.data.network.okx

import retrofit2.http.GET
import retrofit2.http.Query

interface OkxApi {
    //.baseUrl("https://www.okx.com/api/v5/dex/")

    @GET("aggregator/supported/chain")
    suspend fun getSupportedChains(
        @Query("chainId") chainId: String? = null
    ): OkxResponse<OkxChainItem>

     @GET("cross-chain/supported/chain")
    suspend fun getCrossChainSupportedChains(
        @Query("chainId") chainId: String? = null
    ): OkxResponse<OkxChainItem>

    @GET("aggregator/all-tokens")
    suspend fun getAllTokens(
        @Query("chainId") chainId: String? = null
    ): OkxResponse<OkxToken>

   @GET("aggregator/get-liquidity")
    suspend fun getLiquidity(
        @Query("chainId") chainId: String? = null
    ): OkxResponse<OkxLiquidity>

   @GET("aggregator/approve-transaction")
    suspend fun getApproveTransaction(
        @Query("chainId") chainId: String,
        @Query("tokenContractAddress") tokenContractAddress: String,
        @Query("approveAmount") approveAmount: String,
    ): OkxResponse<OkxApproveTransactionsResponse>

   @GET("aggregator/quote")
    suspend fun getQuotes(
        @Query("chainId") chainId: String,
        @Query("amount") amount: String,
        @Query("fromTokenAddress") fromTokenAddress: String,
        @Query("toTokenAddress") toTokenAddress: String,
        @Query("dexIds") dexIds: String? = null,
        @Query("priceImpactProtectionPercentage") priceImpactProtectionPercentage: String? = null,
        @Query("feePercent") feePercent: String? = null,
    ): OkxResponse<OkxQuotesResponse>

   @GET("cross-chain/supported/tokens")
    suspend fun getCrossChainSupportedTokens(
        @Query("chainId") chainId: String? = null
    ): OkxResponse<OkxToken>

   @GET("aggregator/swap")
    suspend fun getOkxSwap(
        @Query("chainId") chainId: String,
        @Query("amount") amount: String,
        @Query("fromTokenAddress") fromTokenAddress: String,
        @Query("toTokenAddress") toTokenAddress: String,
        @Query("slippage") slippage: String,
        @Query("userWalletAddress") userWalletAddress: String,
        @Query("referrerAddress") referrerAddress: String? = null,
        @Query("swapReceiverAddress") swapReceiverAddress: String? = null, // default - userWalletAddress
        @Query("feePercent") feePercent: String? = null,
        @Query("gaslimit") gaslimit: String? = null,
        @Query("gasLevel") gasLevel: String? = null,
        @Query("dexIds") dexIds: String? = null,
        @Query("priceImpactProtectionPercentage") priceImpactProtectionPercentage: String? = null,
        @Query("callDataMemo") callDataMemo: String? = null,
        @Query("toTokenReferrerAddress") toTokenReferrerAddress: String? = null,
        @Query("computeUnitPrice") computeUnitPrice: String? = null,
        @Query("computeUnitLimit") computeUnitLimit: String? = null,
   ): OkxResponse<OkxSwapResponse>

    @GET("cross-chain/build-tx")
    suspend fun crossChainBuildTx(
        @Query("fromChainId") fromChainId: String,
        @Query("toChainId") toChainId: String,
        @Query("fromTokenAddress") fromTokenAddress: String,
        @Query("toTokenAddress") toTokenAddress: String,
        @Query("amount") amount: String,
        @Query("sort") sort: Int? = null, // 0 - default
        @Query("slippage") slippage: String,  // 0.002 - 0.5
        @Query("userWalletAddress") userWalletAddress: String,
        @Query("allowBridge") allowBridge: Array<Int>? = null,
        @Query("denyBridge") denyBridge: Array<Int>? = null,
        @Query("receiveAddress") receiveAddress: String? = null,
        @Query("feePercent") feePercent: String? = null,
        @Query("referrerAddress") referrerAddress: String? = null,
        @Query("priceImpactProtectionPercentage") priceImpactProtectionPercentage: String? = null,
        @Query("onlyBridge") onlyBridge: Boolean? = null,
        @Query("memo") memo: String? = null
    ): OkxResponse<OkxCrossChainResponse>


    fun getAllowance()


}