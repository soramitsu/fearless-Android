package jp.co.soramitsu.common.domain

const val SOLANA_CHAIN_ID = "solana-mainnet"
const val SOLANA_DEVNET_CHAIN_ID = "solana-devnet"
const val SOLANA_DEFAULT_PATH = "m/44'/501'/0'/0'"

fun isSolanaChainId(chainId: String): Boolean {
    return chainId == SOLANA_CHAIN_ID || chainId == SOLANA_DEVNET_CHAIN_ID
}
