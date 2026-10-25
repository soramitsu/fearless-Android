package jp.co.soramitsu.common.data.network.solana

import java.math.BigInteger

/** A reviewable payment; no field or unsigned byte may be rebuilt when the user confirms it. */
data class SolanaPaymentQuote(
    val sender: String,
    val recipient: String,
    val amount: BigInteger,
    val mint: String?,
    val decimals: Int,
    val tokenDebits: List<SolanaTokenDebit>,
    val destinationTokenAccount: String?,
    val createsAssociatedAccount: Boolean,
    val networkFeeLamports: Long,
    val accountRentLamports: Long,
    val priority: SolanaPriorityFee,
    val blockhash: SolanaLatestBlockhash,
    val message: SolanaPaymentMessage
)
