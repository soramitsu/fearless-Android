package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.data.network.solana.SolanaPublicKeyCodec
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/**
 * Whether [address] is a well-formed send recipient for this chain. App-owned Bitcoin and Solana networks
 * carry the legacy Substrate ecosystem value, so they are recognized first and checked in their own format:
 * a Bitcoin address of the chain's network that the wallet can pay, or a 32-byte base58 Solana address.
 * Solana account rules (programs, token accounts, off-curve keys) are checked against the chain when the
 * payment is quoted. Every other chain keeps its ecosystem's format check.
 */
fun Chain.isValidRecipientAddress(address: String): Boolean {
    val normalized = address.trim()
    if (normalized.isEmpty()) return false
    return when {
        isUniversalWalletBitcoin() -> normalizedBitcoinAddress(normalized) != null
        isUniversalWalletSolana() -> runCatching { SolanaPublicKeyCodec.decode(normalized) }.isSuccess
        else -> isValidAddress(normalized)
    }
}

/**
 * Whether [first] and [second] name the same account on this chain. A bech32 Bitcoin address may be written in
 * upper or lower case, so Bitcoin addresses compare in their canonical lowercase form; others compare exactly.
 */
fun Chain.isSameRecipientAddress(first: String, second: String): Boolean {
    if (isUniversalWalletBitcoin()) {
        val canonicalFirst = normalizedBitcoinAddress(first.trim())
        val canonicalSecond = normalizedBitcoinAddress(second.trim())
        if (canonicalFirst != null && canonicalSecond != null) return canonicalFirst == canonicalSecond
    }
    return first == second
}

/** Bitcoin and Solana: payments planned, checked and quoted by the network's own service, not Substrate rules. */
fun Chain.isAppOwnedPaymentNetwork(): Boolean = isUniversalWalletBitcoin() || isUniversalWalletSolana()
