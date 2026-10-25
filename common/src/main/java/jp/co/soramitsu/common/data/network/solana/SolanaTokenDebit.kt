package jp.co.soramitsu.common.data.network.solana

import java.math.BigInteger

/** A precise raw-unit debit from one validated standard SPL token account. */
data class SolanaTokenDebit(val account: String, val amount: BigInteger)
