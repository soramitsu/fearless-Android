package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import java.math.BigDecimal

/** Reads balances exclusively from cluster RPC; unavailable metadata never blocks ownership or sends. */
class SolanaRpcBalanceSource(
    private val client: SolanaRpcClient,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SolanaBalanceSource {
    override suspend fun balances(
        wallet: String,
        network: UniversalWalletRegistry.SolanaNetwork,
        baseUrl: String?,
        includeTokenMetadata: Boolean
    ): SolanaBalanceSyncResult {
        val address = SolanaRpcRoutes.normalizePublicKey(wallet)
        val endpoint = baseUrl ?: network.rpcUrl
        val client = client.withGenesisHash(network.genesisHash).withEndpoints(listOf(endpoint))
        val lamports = client.balance(address, rpcUrl = endpoint)
        require(lamports >= 0) { "Invalid RPC native balance" }
        val spl = client.tokenAccounts(address, SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS, rpcUrl = endpoint)
        val token2022 = client.tokenAccounts(address, SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS, rpcUrl = endpoint)
        val rows = spl + token2022
        require(rows.size <= 1024 && rows.map { it.address }.distinct().size == rows.size) { "Duplicate RPC token account" }
        require(rows.groupBy { it.mint }.all { (_, accounts) ->
            accounts.map { it.decimals to it.ownerProgram }.distinct().size == 1
        }) { "Inconsistent RPC token identity" }
        val timestamp = nowMillis().also { require(it > 0) }
        fun normalized(row: UniversalWalletIndexedAssetBalance): UniversalWalletIndexedAssetBalance {
            require(row.validationErrors().isEmpty()) { "Invalid RPC balance" }
            return row
        }
        val native = normalized(UniversalWalletIndexedAssetBalance(
            accountId = network.id, ecosystem = UniversalWalletEcosystem.Solana, chainId = network.chainId,
            assetId = network.nativeAsset.id, amount = lamports.toString(), decimals = network.nativeAsset.decimals,
            isNative = true, symbol = network.nativeAsset.symbol, name = network.name,
            uiAmountString = BigDecimal.valueOf(lamports, network.nativeAsset.decimals).toPlainString(), syncedAtMillis = timestamp
        ))
        val tokens = rows.map { row ->
            val expectedProgram = if (row in spl) SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
                else SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS
            require(row.walletOwner == address && row.ownerProgram == expectedProgram && row.amount.signum() >= 0 &&
                row.amount.bitLength() <= 64 && row.decimals in 0..255) { "Invalid RPC token identity" }
            SolanaPublicKeyCodec.decode(row.address)
            SolanaPublicKeyCodec.decode(row.mint)
            normalized(UniversalWalletIndexedAssetBalance(
                accountId = network.id, ecosystem = UniversalWalletEcosystem.Solana, chainId = network.chainId,
                assetId = row.mint, amount = row.amount.toString(), decimals = row.decimals, isNative = false,
                symbol = "${row.mint.take(4)}…${row.mint.takeLast(4)}", name = row.mint,
                uiAmountString = BigDecimal(row.amount, row.decimals).toString(), tokenAccountId = row.address,
                contractAddress = row.mint, tokenProgram = if (row.ownerProgram == SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS) {
                    "spl-token"
                } else "token-2022", syncedAtMillis = timestamp
            ))
        }
        return SolanaBalanceSyncResult(address, network.id, network.chainId, timestamp, native, tokens, listOf(native) + tokens,
            rows.filter { it.frozen }.mapTo(mutableSetOf()) { it.address })
    }
}
