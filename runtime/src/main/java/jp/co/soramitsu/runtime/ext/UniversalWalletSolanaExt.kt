package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun Chain.isUniversalWalletSolana(): Boolean {
    return id in SOLANA_CHAIN_IDS || externalApi?.history?.type == Chain.ExternalApi.Section.Type.SOLANA
}

fun Chain.universalWalletSolanaIndexerNetwork(): UniversalWalletRegistry.SolanaNetwork? {
    return when {
        id in SOLANA_DEVNET_CHAIN_IDS -> UniversalWalletRegistry.solanaDevnet
        id in SOLANA_MAINNET_CHAIN_IDS -> UniversalWalletRegistry.solanaMainnet
        externalApi?.history?.type == Chain.ExternalApi.Section.Type.SOLANA && isTestNet -> UniversalWalletRegistry.solanaDevnet
        externalApi?.history?.type == Chain.ExternalApi.Section.Type.SOLANA && !isTestNet -> UniversalWalletRegistry.solanaMainnet
        else -> null
    }
}

fun Chain.solanaAddressFromPublicKey(publicKey: ByteArray): String? {
    return runCatching {
        val address = SolanaKeyDerivation.addressFromPublicKey(publicKey)
        normalizedSolanaAddress(address)
    }.getOrNull()
}

fun Chain.normalizedSolanaAddress(address: String): String? {
    val baseUrl = externalApi?.history?.url ?: UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL

    return runCatching {
        SolanaIndexerRoutes.balancesUrl(address, baseUrl)
        address
    }.getOrNull()
}

private val SOLANA_MAINNET_CHAIN_IDS = setOf(
    UniversalWalletRegistry.solanaMainnet.id,
    UniversalWalletRegistry.solanaMainnet.chainId
)

private val SOLANA_DEVNET_CHAIN_IDS = setOf(
    UniversalWalletRegistry.solanaDevnet.id,
    UniversalWalletRegistry.solanaDevnet.chainId
)

private val SOLANA_CHAIN_IDS = SOLANA_MAINNET_CHAIN_IDS + SOLANA_DEVNET_CHAIN_IDS
