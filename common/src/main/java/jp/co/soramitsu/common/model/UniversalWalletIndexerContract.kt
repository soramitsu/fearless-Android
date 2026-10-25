package jp.co.soramitsu.common.model

import com.google.gson.annotations.SerializedName

data class UniversalWalletIndexedAssetBalance(
    @SerializedName("accountId")
    val accountId: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("assetId")
    val assetId: String,
    @SerializedName("amount")
    val amount: String,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("isNative")
    val isNative: Boolean,
    @SerializedName("symbol")
    val symbol: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("uiAmountString")
    val uiAmountString: String? = null,
    @SerializedName("tokenAccountId")
    val tokenAccountId: String? = null,
    @SerializedName("contractAddress")
    val contractAddress: String? = null,
    @SerializedName("tokenProgram")
    val tokenProgram: String? = null,
    @SerializedName("syncedAtMillis")
    val syncedAtMillis: Long
) {
    constructor(
        accountId: String,
        ecosystem: UniversalWalletEcosystem,
        chainId: String,
        assetId: String,
        amount: String,
        decimals: Int,
        isNative: Boolean,
        symbol: String? = null,
        name: String? = null,
        uiAmountString: String? = null,
        tokenAccountId: String? = null,
        contractAddress: String? = null,
        tokenProgram: String? = null,
        syncedAtMillis: Long
    ) : this(
        accountId = accountId,
        ecosystem = ecosystem.id,
        chainId = chainId,
        assetId = assetId,
        amount = amount,
        decimals = decimals,
        isNative = isNative,
        symbol = symbol,
        name = name,
        uiAmountString = uiAmountString,
        tokenAccountId = tokenAccountId,
        contractAddress = contractAddress,
        tokenProgram = tokenProgram,
        syncedAtMillis = syncedAtMillis
    )

    fun validationErrors(): Set<UniversalWalletIndexerValidationError> {
        val errors = linkedSetOf<UniversalWalletIndexerValidationError>()
        UniversalWalletIndexerContractValidator.validateEnvelope(
            accountId = accountId,
            ecosystem = ecosystem,
            chainId = chainId,
            syncedAtMillis = syncedAtMillis,
            errors = errors
        )
        if (!UniversalWalletIndexerContractValidator.isMachineText(assetId, maxLength = 160)) {
            errors += UniversalWalletIndexerValidationError.InvalidAssetId
        }
        if (!UniversalWalletIndexerContractValidator.isUnsignedInteger(amount)) {
            errors += UniversalWalletIndexerValidationError.InvalidAmount
        }
        if (decimals !in UniversalWalletIndexerContractValidator.DECIMAL_RANGE) {
            errors += UniversalWalletIndexerValidationError.InvalidDecimals
        }
        if (!symbol.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isHumanText(symbol, maxLength = 32)) {
            errors += UniversalWalletIndexerValidationError.InvalidSymbol
        }
        if (!name.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isHumanText(name, maxLength = 96)) {
            errors += UniversalWalletIndexerValidationError.InvalidName
        }
        if (!uiAmountString.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isHumanText(uiAmountString, maxLength = 80)) {
            errors += UniversalWalletIndexerValidationError.InvalidAmount
        }
        if (!tokenAccountId.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isMachineText(tokenAccountId, maxLength = 256)) {
            errors += UniversalWalletIndexerValidationError.InvalidAccountId
        }
        if (!contractAddress.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isMachineText(contractAddress, maxLength = 256)) {
            errors += UniversalWalletIndexerValidationError.InvalidAddress
        }
        if (!tokenProgram.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isMachineText(tokenProgram, maxLength = 64)) {
            errors += UniversalWalletIndexerValidationError.InvalidAssetId
        }

        return errors
    }
}

data class UniversalWalletIndexedTransaction(
    @SerializedName("accountId")
    val accountId: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("transactionId")
    val transactionId: String,
    @SerializedName("status")
    val status: UniversalWalletIndexedTransactionStatus,
    @SerializedName("direction")
    val direction: UniversalWalletIndexedTransactionDirection,
    @SerializedName("operationType")
    val operationType: UniversalWalletIndexedOperationType,
    @SerializedName("timestampMillis")
    val timestampMillis: Long? = null,
    @SerializedName("amount")
    val amount: String? = null,
    @SerializedName("assetId")
    val assetId: String? = null,
    @SerializedName("feeAmount")
    val feeAmount: String? = null,
    @SerializedName("feeAssetId")
    val feeAssetId: String? = null,
    @SerializedName("counterpartyAddress")
    val counterpartyAddress: String? = null,
    @SerializedName("blockNumber")
    val blockNumber: String? = null,
    @SerializedName("cursor")
    val cursor: String? = null,
    @SerializedName("explorerUrl")
    val explorerUrl: String? = null,
    @SerializedName("syncedAtMillis")
    val syncedAtMillis: Long
) {
    fun validationErrors(): Set<UniversalWalletIndexerValidationError> {
        val errors = linkedSetOf<UniversalWalletIndexerValidationError>()
        UniversalWalletIndexerContractValidator.validateEnvelope(
            accountId = accountId,
            ecosystem = ecosystem,
            chainId = chainId,
            syncedAtMillis = syncedAtMillis,
            errors = errors
        )
        if (!UniversalWalletIndexerContractValidator.isMachineText(transactionId, maxLength = 256)) {
            errors += UniversalWalletIndexerValidationError.InvalidTransactionId
        }
        if (timestampMillis != null && timestampMillis <= 0) {
            errors += UniversalWalletIndexerValidationError.InvalidTimestamp
        }
        if (amount != null && !UniversalWalletIndexerContractValidator.isUnsignedInteger(amount)) {
            errors += UniversalWalletIndexerValidationError.InvalidAmount
        }
        if (assetId != null && !UniversalWalletIndexerContractValidator.isMachineText(assetId, maxLength = 160)) {
            errors += UniversalWalletIndexerValidationError.InvalidAssetId
        }
        if (feeAmount != null && !UniversalWalletIndexerContractValidator.isUnsignedInteger(feeAmount)) {
            errors += UniversalWalletIndexerValidationError.InvalidAmount
        }
        if (feeAssetId != null && !UniversalWalletIndexerContractValidator.isMachineText(feeAssetId, maxLength = 160)) {
            errors += UniversalWalletIndexerValidationError.InvalidAssetId
        }
        if (counterpartyAddress != null && !UniversalWalletIndexerContractValidator.isMachineText(counterpartyAddress, maxLength = 256)) {
            errors += UniversalWalletIndexerValidationError.InvalidAddress
        }
        if (blockNumber != null && !UniversalWalletIndexerContractValidator.isUnsignedInteger(blockNumber)) {
            errors += UniversalWalletIndexerValidationError.InvalidBlockNumber
        }
        if (cursor != null && !UniversalWalletIndexerContractValidator.isMachineText(cursor, maxLength = 512)) {
            errors += UniversalWalletIndexerValidationError.InvalidCursor
        }
        if (explorerUrl != null && !UniversalWalletIndexerContractValidator.isHttpsUrl(explorerUrl)) {
            errors += UniversalWalletIndexerValidationError.InvalidUrl
        }

        return errors
    }
}

data class UniversalWalletIndexedTokenMetadata(
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("assetId")
    val assetId: String,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("symbol")
    val symbol: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("iconUrl")
    val iconUrl: String? = null,
    @SerializedName("metadataUrl")
    val metadataUrl: String? = null,
    @SerializedName("isVerified")
    val isVerified: Boolean = false,
    @SerializedName("syncedAtMillis")
    val syncedAtMillis: Long
) {
    fun validationErrors(): Set<UniversalWalletIndexerValidationError> {
        val errors = linkedSetOf<UniversalWalletIndexerValidationError>()

        if (UniversalWalletEcosystem.fromId(ecosystem) == null) {
            errors += UniversalWalletIndexerValidationError.InvalidEcosystem
        }
        if (!UniversalWalletIndexerContractValidator.CHAIN_ID.matches(chainId)) {
            errors += UniversalWalletIndexerValidationError.InvalidChainId
        }
        if (!UniversalWalletIndexerContractValidator.isMachineText(assetId, maxLength = 160)) {
            errors += UniversalWalletIndexerValidationError.InvalidAssetId
        }
        if (decimals !in UniversalWalletIndexerContractValidator.DECIMAL_RANGE) {
            errors += UniversalWalletIndexerValidationError.InvalidDecimals
        }
        if (!symbol.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isHumanText(symbol, maxLength = 32)) {
            errors += UniversalWalletIndexerValidationError.InvalidSymbol
        }
        if (!name.isNullOrBlank() && !UniversalWalletIndexerContractValidator.isHumanText(name, maxLength = 96)) {
            errors += UniversalWalletIndexerValidationError.InvalidName
        }
        if (iconUrl != null && !UniversalWalletIndexerContractValidator.isAssetUrl(iconUrl)) {
            errors += UniversalWalletIndexerValidationError.InvalidUrl
        }
        if (metadataUrl != null && !UniversalWalletIndexerContractValidator.isAssetUrl(metadataUrl)) {
            errors += UniversalWalletIndexerValidationError.InvalidUrl
        }
        if (syncedAtMillis <= 0) {
            errors += UniversalWalletIndexerValidationError.InvalidSyncedAt
        }

        return errors
    }
}

data class UniversalWalletIndexerPageInfo(
    @SerializedName("nextCursor")
    val nextCursor: String? = null,
    @SerializedName("limit")
    val limit: Int,
    @SerializedName("total")
    val total: Int? = null,
    @SerializedName("syncedAtMillis")
    val syncedAtMillis: Long
) {
    fun validationErrors(): Set<UniversalWalletIndexerValidationError> {
        val errors = linkedSetOf<UniversalWalletIndexerValidationError>()

        if (nextCursor != null && !UniversalWalletIndexerContractValidator.isMachineText(nextCursor, maxLength = 512)) {
            errors += UniversalWalletIndexerValidationError.InvalidCursor
        }
        if (limit !in 1..UniversalWalletIndexerContractValidator.MAX_PAGE_LIMIT) {
            errors += UniversalWalletIndexerValidationError.InvalidLimit
        }
        if (total != null && total < 0) {
            errors += UniversalWalletIndexerValidationError.InvalidTotal
        }
        if (syncedAtMillis <= 0) {
            errors += UniversalWalletIndexerValidationError.InvalidSyncedAt
        }

        return errors
    }
}

enum class UniversalWalletIndexedTransactionStatus {
    @SerializedName("pending")
    Pending,

    @SerializedName("confirmed")
    Confirmed,

    @SerializedName("failed")
    Failed
}

enum class UniversalWalletIndexedTransactionDirection {
    @SerializedName("incoming")
    Incoming,

    @SerializedName("outgoing")
    Outgoing,

    @SerializedName("self")
    Self,

    @SerializedName("unknown")
    Unknown
}

enum class UniversalWalletIndexedOperationType {
    @SerializedName("transfer")
    Transfer,

    @SerializedName("swap")
    Swap,

    @SerializedName("stake")
    Stake,

    @SerializedName("unstake")
    Unstake,

    @SerializedName("governance")
    Governance,

    @SerializedName("offline-cash")
    OfflineCash,

    @SerializedName("sccp")
    Sccp,

    @SerializedName("contract-call")
    ContractCall,

    @SerializedName("mint")
    Mint,

    @SerializedName("burn")
    Burn,

    @SerializedName("fee")
    Fee,

    @SerializedName("unknown")
    Unknown
}

enum class UniversalWalletIndexerValidationError {
    InvalidAccountId,
    InvalidEcosystem,
    InvalidChainId,
    InvalidAssetId,
    InvalidAmount,
    InvalidDecimals,
    InvalidSymbol,
    InvalidName,
    InvalidAddress,
    InvalidTransactionId,
    InvalidTimestamp,
    InvalidBlockNumber,
    InvalidCursor,
    InvalidLimit,
    InvalidTotal,
    InvalidUrl,
    InvalidSyncedAt
}

object UniversalWalletIndexerContractValidator {
    val ACCOUNT_ID = Regex("^[a-z0-9][a-z0-9._:-]{1,63}$")
    val CHAIN_ID = Regex("^[A-Za-z0-9._:-]{2,128}$")
    val DECIMAL_RANGE = 0..255
    const val MAX_PAGE_LIMIT = 250

    private val UNSIGNED_INTEGER = Regex("^(0|[1-9][0-9]*)$")
    private val HTTPS_URL = Regex("^https://[^\\s]+$")
    private val ASSET_URL = Regex("^(https|ipfs)://[^\\s]+$")

    fun validateEnvelope(
        accountId: String,
        ecosystem: String,
        chainId: String,
        syncedAtMillis: Long,
        errors: MutableSet<UniversalWalletIndexerValidationError>
    ) {
        if (!ACCOUNT_ID.matches(accountId)) {
            errors += UniversalWalletIndexerValidationError.InvalidAccountId
        }
        if (UniversalWalletEcosystem.fromId(ecosystem) == null) {
            errors += UniversalWalletIndexerValidationError.InvalidEcosystem
        }
        if (!CHAIN_ID.matches(chainId)) {
            errors += UniversalWalletIndexerValidationError.InvalidChainId
        }
        if (syncedAtMillis <= 0) {
            errors += UniversalWalletIndexerValidationError.InvalidSyncedAt
        }
    }

    fun isUnsignedInteger(value: String): Boolean = UNSIGNED_INTEGER.matches(value)

    fun isHttpsUrl(value: String): Boolean = HTTPS_URL.matches(value)

    fun isAssetUrl(value: String): Boolean = ASSET_URL.matches(value)

    fun isHumanText(value: String, maxLength: Int): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() &&
            normalized.length <= maxLength &&
            normalized.none { it.isISOControl() }
    }

    fun isMachineText(value: String, maxLength: Int): Boolean {
        return value.isNotEmpty() &&
            value.length <= maxLength &&
            value == value.trim() &&
            value.none { it <= ' ' || it.isISOControl() }
    }
}
