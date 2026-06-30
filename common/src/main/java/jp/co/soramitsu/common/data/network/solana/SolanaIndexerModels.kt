package jp.co.soramitsu.common.data.network.solana

import com.google.gson.annotations.SerializedName

data class SolanaIndexerServiceInfo(
    @SerializedName("schemaVersion")
    val schemaVersion: Int,
    @SerializedName("serviceId")
    val serviceId: String,
    @SerializedName("serviceName")
    val serviceName: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("network")
    val network: String,
    @SerializedName("publicBaseUrl")
    val publicBaseUrl: String,
    @SerializedName("readOnly")
    val readOnly: Boolean,
    @SerializedName("capabilities")
    val capabilities: List<String> = emptyList(),
    @SerializedName("endpoints")
    val endpoints: Map<String, String> = emptyMap()
)

fun SolanaIndexerServiceInfo.isExpectedSiServiceInfo(): Boolean {
    return schemaVersion == 1 &&
        serviceId == "si.soramitsu.io" &&
        ecosystem == "solana" &&
        chainId == "solana:mainnet" &&
        publicBaseUrl == "https://si.soramitsu.io" &&
        readOnly
}

data class SolanaNativeBalance(
    @SerializedName("type")
    val type: String = "native",
    @SerializedName("mint")
    val mint: String = "SOL",
    @SerializedName("lamports")
    val lamports: String,
    @SerializedName("decimals")
    val decimals: Int = 9,
    @SerializedName("uiAmountString")
    val uiAmountString: String
)

data class SolanaTokenBalance(
    @SerializedName("type")
    val type: String = "token",
    @SerializedName("accountAddress")
    val accountAddress: String,
    @SerializedName("mint")
    val mint: String,
    @SerializedName("owner")
    val owner: String,
    @SerializedName("program")
    val program: String,
    @SerializedName("programId")
    val programId: String,
    @SerializedName("amount")
    val amount: String,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("uiAmountString")
    val uiAmountString: String,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("isNative")
    val isNative: Boolean = false,
    @SerializedName("delegatedAmount")
    val delegatedAmount: String? = null,
    @SerializedName("rentExemptReserve")
    val rentExemptReserve: String? = null
)

data class SolanaWalletBalancesResponse(
    @SerializedName("wallet")
    val wallet: String,
    @SerializedName("native")
    val native: SolanaNativeBalance,
    @SerializedName("tokens")
    val tokens: List<SolanaTokenBalance> = emptyList(),
    @SerializedName("total")
    val total: Int,
    @SerializedName("syncedAt")
    val syncedAt: Long
)

data class SolanaWalletAssetsResponse(
    @SerializedName("wallet")
    val wallet: String,
    @SerializedName("assets")
    val assets: List<SolanaWalletAsset> = emptyList(),
    @SerializedName("total")
    val total: Int,
    @SerializedName("syncedAt")
    val syncedAt: Long
)

data class SolanaWalletAsset(
    @SerializedName("type")
    val type: String,
    @SerializedName("mint")
    val mint: String,
    @SerializedName("lamports")
    val lamports: String? = null,
    @SerializedName("accountAddress")
    val accountAddress: String? = null,
    @SerializedName("owner")
    val owner: String? = null,
    @SerializedName("program")
    val program: String? = null,
    @SerializedName("programId")
    val programId: String? = null,
    @SerializedName("amount")
    val amount: String? = null,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("uiAmountString")
    val uiAmountString: String,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("isNative")
    val isNative: Boolean? = null,
    @SerializedName("delegatedAmount")
    val delegatedAmount: String? = null,
    @SerializedName("rentExemptReserve")
    val rentExemptReserve: String? = null
)

data class SolanaWalletStateResponse(
    @SerializedName("wallet")
    val wallet: String,
    @SerializedName("exists")
    val exists: Boolean,
    @SerializedName("lamports")
    val lamports: String,
    @SerializedName("owner")
    val owner: String? = null,
    @SerializedName("executable")
    val executable: Boolean,
    @SerializedName("rentEpoch")
    val rentEpoch: String? = null,
    @SerializedName("dataLength")
    val dataLength: Int,
    @SerializedName("syncedAt")
    val syncedAt: Long
)

data class SolanaTokenBalanceChange(
    @SerializedName("mint")
    val mint: String,
    @SerializedName("preAmount")
    val preAmount: String,
    @SerializedName("postAmount")
    val postAmount: String,
    @SerializedName("amountDelta")
    val amountDelta: String,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("uiAmountDeltaString")
    val uiAmountDeltaString: String
)

data class SolanaWalletTransactionRecord(
    @SerializedName("signature")
    val signature: String,
    @SerializedName("slot")
    val slot: Long,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("feeLamports")
    val feeLamports: String? = null,
    @SerializedName("nativeBalanceChangeLamports")
    val nativeBalanceChangeLamports: String? = null,
    @SerializedName("tokenBalanceChanges")
    val tokenBalanceChanges: List<SolanaTokenBalanceChange> = emptyList(),
    @SerializedName("programIds")
    val programIds: List<String> = emptyList(),
    @SerializedName("solswapRoute")
    val solswapRoute: String? = null
)

data class SolanaWalletTransactionsResponse(
    @SerializedName("wallet")
    val wallet: String,
    @SerializedName("before")
    val before: String? = null,
    @SerializedName("nextBefore")
    val nextBefore: String? = null,
    @SerializedName("limit")
    val limit: Int,
    @SerializedName("total")
    val total: Int,
    @SerializedName("syncedAt")
    val syncedAt: Long,
    @SerializedName("transactions")
    val transactions: List<SolanaWalletTransactionRecord> = emptyList()
)

data class SolanaTokenMetadata(
    @SerializedName("mint")
    val mint: String,
    @SerializedName("exists")
    val exists: Boolean,
    @SerializedName("program")
    val program: String,
    @SerializedName("programId")
    val programId: String? = null,
    @SerializedName("extensions")
    val extensions: List<String>? = null,
    @SerializedName("transferFeeConfig")
    val transferFeeConfig: SolanaTokenTransferFeeConfig? = null,
    @SerializedName("transferHook")
    val transferHook: SolanaTokenTransferHook? = null,
    @SerializedName("decimals")
    val decimals: Int? = null,
    @SerializedName("supply")
    val supply: String? = null,
    @SerializedName("uiSupplyString")
    val uiSupplyString: String? = null,
    @SerializedName("mintAuthority")
    val mintAuthority: String? = null,
    @SerializedName("freezeAuthority")
    val freezeAuthority: String? = null,
    @SerializedName("isInitialized")
    val isInitialized: Boolean? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("symbol")
    val symbol: String? = null,
    @SerializedName("uri")
    val uri: String? = null,
    @SerializedName("syncedAt")
    val syncedAt: Long
)

data class SolanaTokenTransferFee(
    @SerializedName("epoch")
    val epoch: String? = null,
    @SerializedName("maximumFee")
    val maximumFee: String? = null,
    @SerializedName("transferFeeBasisPoints")
    val transferFeeBasisPoints: Int? = null
)

data class SolanaTokenTransferFeeConfig(
    @SerializedName("transferFeeConfigAuthority")
    val transferFeeConfigAuthority: String? = null,
    @SerializedName("withdrawWithheldAuthority")
    val withdrawWithheldAuthority: String? = null,
    @SerializedName("withheldAmount")
    val withheldAmount: String? = null,
    @SerializedName("olderTransferFee")
    val olderTransferFee: SolanaTokenTransferFee? = null,
    @SerializedName("newerTransferFee")
    val newerTransferFee: SolanaTokenTransferFee? = null
)

data class SolanaTokenTransferHook(
    @SerializedName("authority")
    val authority: String? = null,
    @SerializedName("programId")
    val programId: String? = null,
    @SerializedName("extraAccountMetasAddress")
    val extraAccountMetasAddress: String? = null
)

data class SolanaTokenMetadataBatchRequest(
    @SerializedName("mints")
    val mints: List<String>
)

data class SolanaTokenMetadataBatchResponse(
    @SerializedName("total")
    val total: Int,
    @SerializedName("syncedAt")
    val syncedAt: Long,
    @SerializedName("tokens")
    val tokens: List<SolanaTokenMetadata> = emptyList()
)
