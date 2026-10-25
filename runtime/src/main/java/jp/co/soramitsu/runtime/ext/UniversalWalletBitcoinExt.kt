package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun Chain.isUniversalWalletBitcoin(): Boolean {
    return id in BITCOIN_CHAIN_IDS || externalApi?.history?.type == Chain.ExternalApi.Section.Type.BITCOIN
}

fun Chain.universalWalletBitcoinKeyNetwork(): BitcoinKeyDerivation.Network? {
    return when {
        id in BITCOIN_TESTNET_CHAIN_IDS -> BitcoinKeyDerivation.Network.Testnet
        id in BITCOIN_MAINNET_CHAIN_IDS -> BitcoinKeyDerivation.Network.Mainnet
        externalApi?.history?.type == Chain.ExternalApi.Section.Type.BITCOIN && isTestNet -> BitcoinKeyDerivation.Network.Testnet
        externalApi?.history?.type == Chain.ExternalApi.Section.Type.BITCOIN -> BitcoinKeyDerivation.Network.Mainnet
        else -> null
    }
}

fun Chain.universalWalletBitcoinIndexerNetwork(): BitcoinIndexerRoutes.Network? {
    return when (universalWalletBitcoinKeyNetwork()) {
        BitcoinKeyDerivation.Network.Mainnet -> BitcoinIndexerRoutes.Network.Mainnet
        BitcoinKeyDerivation.Network.Testnet -> BitcoinIndexerRoutes.Network.Testnet
        null -> null
    }
}

fun Chain.bitcoinAddressFromPublicKey(publicKey: ByteArray): String? {
    val keyNetwork = universalWalletBitcoinKeyNetwork() ?: return null
    val indexerNetwork = universalWalletBitcoinIndexerNetwork() ?: return null

    return runCatching {
        val address = BitcoinKeyDerivation.addressFromPublicKey(publicKey, keyNetwork)
        BitcoinIndexerRoutes.normalizeAddress(address, indexerNetwork)
    }.getOrNull()
}

fun Chain.normalizedBitcoinAddress(address: String): String? {
    val indexerNetwork = universalWalletBitcoinIndexerNetwork() ?: return null

    return runCatching {
        BitcoinIndexerRoutes.normalizeAddress(address, indexerNetwork)
    }.getOrNull()
}

private val BITCOIN_MAINNET_CHAIN_IDS = setOf(
    UniversalWalletRegistry.bitcoinMainnet.id,
    UniversalWalletRegistry.bitcoinMainnet.chainId
)

private val BITCOIN_TESTNET_CHAIN_IDS = setOf(
    UniversalWalletRegistry.bitcoinTestnet.id,
    UniversalWalletRegistry.bitcoinTestnet.chainId
)

private val BITCOIN_CHAIN_IDS = BITCOIN_MAINNET_CHAIN_IDS + BITCOIN_TESTNET_CHAIN_IDS
