package jp.co.soramitsu.common.data.network.solana

import java.math.BigInteger

/** A validated token-account row whose owner, mint, program, amount and decimals remain exact. */
data class SolanaRpcTokenAccount(
    val address: String,
    val walletOwner: String,
    val mint: String,
    val ownerProgram: String,
    val amount: BigInteger,
    val decimals: Int,
    val frozen: Boolean
)
