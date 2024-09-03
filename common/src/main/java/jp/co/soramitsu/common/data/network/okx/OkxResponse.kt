package jp.co.soramitsu.common.data.network.okx

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

data class OkxToken(
    val decimals: String,
    val tokenContractAddress: String,
    val tokenLogoUrl: String,
    val tokenName: String,
    val tokenSymbol: String
)
