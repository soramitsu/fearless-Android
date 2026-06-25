package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.CancellationException

class SolanaBalanceSync(
    private val client: SolanaIndexerClient
) {
    suspend fun balances(
        wallet: String,
        network: UniversalWalletRegistry.SolanaNetwork = UniversalWalletRegistry.solanaMainnet,
        baseUrl: String? = null,
        includeTokenMetadata: Boolean = true
    ): SolanaBalanceSyncResult {
        val resolvedBaseUrl = baseUrl ?: network.indexerBaseUrl
        SolanaIndexerRoutes.balancesUrl(wallet, resolvedBaseUrl)

        client.verifyServiceInfo(resolvedBaseUrl)
        val response = client.balances(wallet, resolvedBaseUrl)
        if (response.wallet != wallet) {
            throw SolanaBalanceSyncException(SolanaBalanceSyncException.Code.WALLET_MISMATCH)
        }
        if (response.syncedAt <= 0) {
            throw SolanaBalanceSyncException(SolanaBalanceSyncException.Code.INVALID_SYNC_TIMESTAMP)
        }

        val native = nativeBalance(response.native, wallet, network, response.syncedAt)
        val normalizedTokens = response.tokens.mapNotNull { tokenBalanceOrNull(it, wallet, network, response.syncedAt) }
        val metadataByMint = if (includeTokenMetadata) {
            tokenMetadataByMint(normalizedTokens.map { it.source.mint }, resolvedBaseUrl)
        } else {
            emptyMap()
        }
        val tokens = normalizedTokens.map { normalized ->
            normalized.balance.copy(
                symbol = tokenSymbol(normalized.source.mint, metadataByMint[normalized.source.mint]),
                name = tokenName(normalized.source.mint, metadataByMint[normalized.source.mint])
            )
        }
        val allBalances = listOf(native) + tokens

        return SolanaBalanceSyncResult(
            wallet = wallet,
            networkId = network.id,
            chainId = network.chainId,
            syncedAtMillis = response.syncedAt,
            nativeBalance = native,
            tokenBalances = tokens,
            balances = allBalances
        )
    }

    private fun nativeBalance(
        native: SolanaNativeBalance,
        wallet: String,
        network: UniversalWalletRegistry.SolanaNetwork,
        syncedAtMillis: Long
    ): UniversalWalletIndexedAssetBalance {
        if (native.type != "native" ||
            native.mint != network.nativeAsset.id ||
            !isUnsignedInteger(native.lamports) ||
            native.decimals !in DECIMAL_RANGE ||
            !isHumanText(native.uiAmountString, 80)
        ) {
            throw SolanaBalanceSyncException(SolanaBalanceSyncException.Code.INVALID_NATIVE_BALANCE)
        }

        return UniversalWalletIndexedAssetBalance(
            accountId = network.id,
            ecosystem = UniversalWalletEcosystem.Solana,
            chainId = network.chainId,
            assetId = network.nativeAsset.id,
            amount = native.lamports,
            decimals = native.decimals,
            isNative = true,
            symbol = network.nativeAsset.symbol,
            name = network.name,
            uiAmountString = native.uiAmountString,
            syncedAtMillis = syncedAtMillis
        ).requireValid(SolanaBalanceSyncException.Code.INVALID_NATIVE_BALANCE)
    }

    private fun tokenBalanceOrNull(
        token: SolanaTokenBalance,
        wallet: String,
        network: UniversalWalletRegistry.SolanaNetwork,
        syncedAtMillis: Long
    ): NormalizedSolanaTokenBalance? {
        if (token.type != "token" ||
            token.owner != wallet ||
            token.isNative ||
            token.program !in SUPPORTED_TOKEN_PROGRAMS ||
            !isBase58PublicKey(token.accountAddress) ||
            !isBase58PublicKey(token.mint) ||
            !isBase58PublicKey(token.programId) ||
            !isUnsignedInteger(token.amount) ||
            token.decimals !in DECIMAL_RANGE ||
            !isHumanText(token.uiAmountString, 80)
        ) {
            return null
        }

        val balance = UniversalWalletIndexedAssetBalance(
            accountId = network.id,
            ecosystem = UniversalWalletEcosystem.Solana,
            chainId = network.chainId,
            assetId = token.mint,
            amount = token.amount,
            decimals = token.decimals,
            isNative = false,
            symbol = shortenMint(token.mint),
            name = token.mint,
            uiAmountString = token.uiAmountString,
            tokenAccountId = token.accountAddress,
            contractAddress = token.mint,
            tokenProgram = token.program,
            syncedAtMillis = syncedAtMillis
        )

        return if (balance.validationErrors().isEmpty()) {
            NormalizedSolanaTokenBalance(source = token, balance = balance)
        } else {
            null
        }
    }

    private suspend fun tokenMetadataByMint(
        mints: List<String>,
        baseUrl: String
    ): Map<String, SolanaTokenMetadata> {
        val uniqueMints = mints.distinct()
        if (uniqueMints.isEmpty()) {
            return emptyMap()
        }

        val metadata = mutableMapOf<String, SolanaTokenMetadata>()
        try {
            uniqueMints.chunked(SolanaIndexerRoutes.MAX_METADATA_BATCH_SIZE).forEach { chunk ->
                client.tokenMetadataBatch(chunk, baseUrl).tokens.forEach { tokenMetadata ->
                    if (tokenMetadata.exists && isBase58PublicKey(tokenMetadata.mint)) {
                        metadata[tokenMetadata.mint] = tokenMetadata
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return emptyMap()
        }

        return metadata
    }

    private fun tokenSymbol(mint: String, metadata: SolanaTokenMetadata?): String {
        return safeHumanText(metadata?.symbol, 32) ?: shortenMint(mint)
    }

    private fun tokenName(mint: String, metadata: SolanaTokenMetadata?): String {
        return safeHumanText(metadata?.name, 96) ?: mint
    }

    private fun UniversalWalletIndexedAssetBalance.requireValid(
        code: SolanaBalanceSyncException.Code
    ): UniversalWalletIndexedAssetBalance {
        if (validationErrors().isNotEmpty()) {
            throw SolanaBalanceSyncException(code)
        }

        return this
    }

    private data class NormalizedSolanaTokenBalance(
        val source: SolanaTokenBalance,
        val balance: UniversalWalletIndexedAssetBalance
    )

    companion object {
        private val DECIMAL_RANGE = 0..255
        private val UNSIGNED_INTEGER = Regex("^(0|[1-9][0-9]*)$")
        private val BASE58_PUBLIC_KEY = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
        private val CONTROL_CHARACTERS = Regex("[\\p{Cntrl}]")
        private val SUPPORTED_TOKEN_PROGRAMS = setOf("spl-token", "token-2022")

        private fun isUnsignedInteger(value: String): Boolean {
            return UNSIGNED_INTEGER.matches(value)
        }

        private fun isBase58PublicKey(value: String): Boolean {
            return BASE58_PUBLIC_KEY.matches(value)
        }

        private fun isHumanText(value: String, maxLength: Int): Boolean {
            val normalized = value.trim()
            return normalized.isNotEmpty() &&
                normalized.length <= maxLength &&
                !CONTROL_CHARACTERS.containsMatchIn(normalized)
        }

        private fun safeHumanText(value: String?, maxLength: Int): String? {
            val normalized = value?.trim().orEmpty()
            return normalized.takeIf { isHumanText(it, maxLength) }
        }

        private fun shortenMint(mint: String): String {
            return if (mint.length <= 12) {
                mint
            } else {
                "${mint.take(4)}...${mint.takeLast(4)}"
            }
        }
    }
}

data class SolanaBalanceSyncResult(
    val wallet: String,
    val networkId: String,
    val chainId: String,
    val syncedAtMillis: Long,
    val nativeBalance: UniversalWalletIndexedAssetBalance,
    val tokenBalances: List<UniversalWalletIndexedAssetBalance>,
    val balances: List<UniversalWalletIndexedAssetBalance>
)

class SolanaBalanceSyncException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        WALLET_MISMATCH,
        INVALID_SYNC_TIMESTAMP,
        INVALID_NATIVE_BALANCE
    }
}
