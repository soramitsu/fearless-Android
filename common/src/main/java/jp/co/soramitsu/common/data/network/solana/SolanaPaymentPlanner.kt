package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.data.network.solana.SolanaPaymentQuoteException.Code
import java.math.BigInteger
import jp.co.soramitsu.core.utils.toLongExact

/** RPC-only preflight and immutable messages for native SOL and standard SPL payments. */
class SolanaPaymentPlanner(private val client: SolanaRpcClient) {
    /** Quotes native transfer or an exact sweep; account rent and fee are resolved before review. */
    suspend fun native(sender: String, recipient: String, amount: Long, rpcUrl: String? = null, sendMax: Boolean = false): SolanaPaymentQuote {
        SolanaAccountPreflight.requireWallet(sender, client.accountInfo(sender, rpcUrl = rpcUrl))
        val destination = client.accountInfo(recipient, rpcUrl = rpcUrl)
        SolanaAccountPreflight.requireWallet(recipient, destination)
        val balance = client.balance(sender, rpcUrl = rpcUrl)
        val rent = client.minimumBalanceForRentExemption(0, rpcUrl = rpcUrl)
        val priority = SolanaPriorityFee.fromSamples(client.recentPrioritizationFees(rpcUrl), tokenTransfer = false)
        val blockhash = client.latestBlockhash(rpcUrl = rpcUrl).value
        val provisional = SolanaPaymentMessageBuilder.native(sender, recipient, if (sendMax) 1 else amount, blockhash.blockhash, priority)
        val fee = fee(provisional, rpcUrl)
        val finalAmount = if (sendMax) (BigInteger.valueOf(balance) - BigInteger.valueOf(fee)).toLongExact() else amount
        if (finalAmount <= 0) throw SolanaAccountPreflightException(SolanaAccountPreflightException.Code.INSUFFICIENT_BALANCE)
        SolanaAccountPreflight.requireNativeRent(balance, finalAmount, fee, rent, destination?.lamports ?: 0L)
        val message = if (sendMax) SolanaPaymentMessageBuilder.native(sender, recipient, finalAmount, blockhash.blockhash, priority) else provisional
        if (sendMax && fee(message, rpcUrl) != fee) fail(Code.REVIEW_CHANGED)
        return SolanaPaymentQuote(sender, recipient, BigInteger.valueOf(finalAmount), null, 9, emptyList(), null, false, fee, 0, priority, blockhash, message)
    }

    /** Uses all matching spendable owner accounts as needed; full unsigned-u64 SPL amounts are supported. */
    suspend fun token(sender: String, recipient: String, mint: String, decimals: Int, amount: BigInteger,
        rpcUrl: String? = null, sendMax: Boolean = false): SolanaPaymentQuote {
        SolanaAccountPreflight.requireWallet(sender, client.accountInfo(sender, rpcUrl = rpcUrl))
        SolanaAccountPreflight.requireWallet(recipient, client.accountInfo(recipient, rpcUrl = rpcUrl))
        SolanaAccountPreflight.requireMint(client.accountInfo(mint, rpcUrl = rpcUrl), decimals)
        val accounts = client.tokenAccounts(sender, SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS, rpcUrl = rpcUrl)
            .filter { it.mint == mint && !it.frozen && it.amount.signum() > 0 }
            .sortedBy { it.address }
        require(accounts.map { it.address }.distinct().size == accounts.size)
        require(accounts.all { it.walletOwner == sender && it.ownerProgram == SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS &&
            it.decimals == decimals && it.amount.signum() >= 0 && it.amount <= MAX_U64 })
        val available = accounts.fold(BigInteger.ZERO) { total, row -> total + row.amount }
        require(available <= MAX_U64)
        val requested = if (sendMax) available else amount
        require(requested.signum() > 0 && requested <= MAX_U64)
        var remaining = requested
        val debits = mutableListOf<SolanaTokenDebit>()
        for (account in accounts) {
            if (remaining == BigInteger.ZERO) break
            val current = SolanaAccountPreflight.tokenBalance(client.accountInfo(account.address, rpcUrl = rpcUrl), mint, sender)
            // A row changing between inventory and proof returns to review, never silently shifts coins.
            if (current != account.amount) fail(Code.REVIEW_CHANGED)
            val debit = minOf(current, remaining)
            debits += SolanaTokenDebit(account.address, debit)
            remaining -= debit
            if (debits.size > 16) fail(Code.TOO_MANY_TOKEN_ACCOUNTS)
        }
        if (remaining.signum() > 0) fail(Code.INSUFFICIENT_TOKEN_BALANCE)
        val destination = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(recipient, mint)
        val destinationInfo = client.accountInfo(destination, rpcUrl = rpcUrl)
        if (destinationInfo != null) SolanaAccountPreflight.tokenBalance(destinationInfo, mint, recipient)
        val create = destinationInfo == null
        val accountRent = if (create) client.minimumBalanceForRentExemption(165, rpcUrl = rpcUrl) else 0L
        val walletRent = client.minimumBalanceForRentExemption(0, rpcUrl = rpcUrl)
        val priority = SolanaPriorityFee.fromSamples(client.recentPrioritizationFees(rpcUrl), tokenTransfer = true)
        val blockhash = client.latestBlockhash(rpcUrl = rpcUrl).value
        val message = SolanaPaymentMessageBuilder.token(sender, recipient, mint, debits, decimals, create, blockhash.blockhash, priority)
        val fee = fee(message, rpcUrl)
        SolanaAccountPreflight.requireTokenRent(client.balance(sender, rpcUrl = rpcUrl), fee, accountRent, walletRent)
        return SolanaPaymentQuote(sender, recipient, requested, mint, decimals, debits.toList(), destination, create, fee, accountRent, priority, blockhash, message)
    }

    /** Rechecks the reviewed account/fee/creation decision and expiry without fetching a new blockhash. */
    suspend fun revalidate(quote: SolanaPaymentQuote, rpcUrl: String? = null) {
        requireUnexpired(quote, rpcUrl)
        SolanaAccountPreflight.requireWallet(quote.sender, client.accountInfo(quote.sender, rpcUrl = rpcUrl))
        val recipient = client.accountInfo(quote.recipient, rpcUrl = rpcUrl)
        SolanaAccountPreflight.requireWallet(quote.recipient, recipient)
        if (fee(quote.message, rpcUrl) != quote.networkFeeLamports) fail(Code.REVIEW_CHANGED)
        val balance = client.balance(quote.sender, rpcUrl = rpcUrl)
        val walletRent = client.minimumBalanceForRentExemption(0, rpcUrl = rpcUrl)
        if (quote.mint == null) {
            SolanaAccountPreflight.requireNativeRent(balance, quote.amount.toLongExact(), quote.networkFeeLamports, walletRent, recipient?.lamports ?: 0L)
        } else {
            SolanaAccountPreflight.requireMint(client.accountInfo(quote.mint, rpcUrl = rpcUrl), quote.decimals)
            quote.tokenDebits.forEach { debit ->
                val available = SolanaAccountPreflight.tokenBalance(client.accountInfo(debit.account, rpcUrl = rpcUrl), quote.mint, quote.sender)
                if (available < debit.amount) fail(Code.REVIEW_CHANGED)
            }
            val destination = client.accountInfo(requireNotNull(quote.destinationTokenAccount), rpcUrl = rpcUrl)
            if ((destination == null) != quote.createsAssociatedAccount) fail(Code.REVIEW_CHANGED)
            if (destination != null) SolanaAccountPreflight.tokenBalance(destination, quote.mint, quote.recipient)
            val rent = if (quote.createsAssociatedAccount) client.minimumBalanceForRentExemption(165, rpcUrl = rpcUrl) else 0L
            if (rent != quote.accountRentLamports) fail(Code.REVIEW_CHANGED)
            SolanaAccountPreflight.requireTokenRent(balance, quote.networkFeeLamports, rent, walletRent)
        }
    }

    /** Expiry is checked before the first submission; an expired ambiguous send still needs status resolution. */
    suspend fun requireUnexpired(quote: SolanaPaymentQuote, rpcUrl: String? = null) {
        if (client.blockHeight(rpcUrl = rpcUrl) > quote.blockhash.lastValidBlockHeight) fail(Code.BLOCKHASH_EXPIRED)
    }

    private suspend fun fee(message: SolanaPaymentMessage, rpcUrl: String?): Long =
        client.feeForMessage(message.messageBase64, rpcUrl = rpcUrl).value?.takeIf { it >= 0 } ?: fail(Code.FEE_UNAVAILABLE)

    private fun fail(code: Code): Nothing = throw SolanaPaymentQuoteException(code)

    private companion object { val MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE) }
}
