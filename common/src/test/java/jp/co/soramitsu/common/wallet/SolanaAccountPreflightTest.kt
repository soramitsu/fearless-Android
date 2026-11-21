package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaAccountPreflight
import jp.co.soramitsu.common.data.network.solana.SolanaAccountPreflightException
import jp.co.soramitsu.common.data.network.solana.SolanaAccountPreflightException.Code
import jp.co.soramitsu.common.data.network.solana.SolanaPublicKeyCodec
import jp.co.soramitsu.common.data.network.solana.SolanaRpcAccountInfo
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class SolanaAccountPreflightTest {
    @Test
    fun `wallet recipients must be signing capable and system owned with no program data`() {
        SolanaAccountPreflight.requireWallet(WALLET, null)
        SolanaAccountPreflight.requireWallet(WALLET, account(ByteArray(0), SYSTEM))
        fails(Code.RECIPIENT_CANNOT_SIGN) { SolanaAccountPreflight.requireWallet(SYSTEM, null) }
        val ata = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(WALLET, MINT)
        fails(Code.RECIPIENT_CANNOT_SIGN) { SolanaAccountPreflight.requireWallet(ata, null) }
        for (invalid in listOf(account(ByteArray(165)), account(ByteArray(82)), account(ByteArray(0), SYSTEM).copy(executable = true),
            account(byteArrayOf(0), SYSTEM))) {
            fails(Code.RECIPIENT_IS_NOT_WALLET) { SolanaAccountPreflight.requireWallet(WALLET, invalid) }
        }
    }

    @Test
    fun `SOL may go to an existing off-curve System account but never to a missing off-curve address or a program`() {
        val vault = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(WALLET, MINT)
        // A funded multisig vault at a program-derived address is a plain System account.
        SolanaAccountPreflight.requireNativeRecipient(vault, account(ByteArray(0), SYSTEM))
        SolanaAccountPreflight.requireNativeRecipient(WALLET, null)
        SolanaAccountPreflight.requireNativeRecipient(WALLET, account(ByteArray(0), SYSTEM))
        fails(Code.RECIPIENT_CANNOT_SIGN) { SolanaAccountPreflight.requireNativeRecipient(vault, null) }
        fails(Code.RECIPIENT_CANNOT_SIGN) { SolanaAccountPreflight.requireNativeRecipient(SYSTEM, null) }
        fails(Code.RECIPIENT_CANNOT_SIGN) { SolanaAccountPreflight.requireNativeRecipient("not-base58", null) }
        for (invalid in listOf(account(tokenData()), account(tokenData(), TOKEN2022), account(ByteArray(0), SYSTEM).copy(executable = true),
            account(byteArrayOf(0), SYSTEM), account(ByteArray(0), "BPFLoaderUpgradeab1e11111111111111111111111"))) {
            fails(Code.RECIPIENT_IS_NOT_WALLET) { SolanaAccountPreflight.requireNativeRecipient(vault, invalid) }
            fails(Code.RECIPIENT_IS_NOT_WALLET) { SolanaAccountPreflight.requireNativeRecipient(WALLET, invalid) }
        }
        // Token sends keep requiring a signing owner key.
        fails(Code.RECIPIENT_CANNOT_SIGN) { SolanaAccountPreflight.requireWallet(vault, account(ByteArray(0), SYSTEM)) }
    }

    @Test
    fun `mint requires exact program initialized data and expected decimals`() {
        val mint = ByteArray(82).also { it[44] = 6; it[45] = 1 }
        SolanaAccountPreflight.requireMint(account(mint), 6)
        for (invalid in listOf(null, account(mint, TOKEN2022), account(mint).copy(executable = true),
            account(mint.copyOf(165)), account(mint.clone().also { it[45] = 0 }), account(mint.clone().also { it[0] = 2 }))) {
            fails(Code.INVALID_TOKEN_MINT) { SolanaAccountPreflight.requireMint(invalid, 6) }
        }
        fails(Code.INVALID_TOKEN_MINT) { SolanaAccountPreflight.requireMint(account(mint), 9) }
    }

    @Test
    fun `ATA lookup rejects wrong mint wallet or token program and frozen source retains full unsigned amount`() {
        val data = tokenData()
        assertEquals(BigInteger("18446744073709551615"), SolanaAccountPreflight.tokenBalance(account(data), MINT, WALLET))
        for (invalid in listOf(null, account(data, TOKEN2022), account(data).copy(executable = true), account(data.copyOf(82)),
            account(data.clone().also { it[0] = (it[0].toInt() xor 1).toByte() }),
            account(data.clone().also { it[32] = (it[32].toInt() xor 1).toByte() }),
            account(data.clone().also { it[108] = 0 }), account(data.clone().also { it[109] = 2 }))) {
            fails(Code.INVALID_TOKEN_ACCOUNT) { SolanaAccountPreflight.tokenBalance(invalid, MINT, WALLET) }
        }
        fails(Code.TOKEN_ACCOUNT_FROZEN) { SolanaAccountPreflight.tokenBalance(account(data.clone().also { it[108] = 2 }), MINT, WALLET) }
    }

    @Test
    fun `native recipient rent and sender remainder are checked without overflow and exact sweep is valid`() {
        SolanaAccountPreflight.requireNativeRent(105, 100, 5, 100, 0)
        SolanaAccountPreflight.requireNativeRent(205, 100, 5, 100, 0)
        fails(Code.SENDER_BELOW_RENT) { SolanaAccountPreflight.requireNativeRent(106, 100, 5, 100, 0) }
        fails(Code.RECIPIENT_BELOW_RENT) { SolanaAccountPreflight.requireNativeRent(104, 99, 5, 100, 0) }
        fails(Code.INSUFFICIENT_BALANCE) { SolanaAccountPreflight.requireNativeRent(Long.MAX_VALUE, Long.MAX_VALUE, 1, 1, Long.MAX_VALUE) }
        SolanaAccountPreflight.requireNativeRent(Long.MAX_VALUE, Long.MAX_VALUE, 0, 1, Long.MAX_VALUE)
    }

    @Test
    fun `token creation rent is separate from fee and never leaves a nonexempt sender`() {
        SolanaAccountPreflight.requireTokenRent(205, 5, 100, 100)
        SolanaAccountPreflight.requireTokenRent(105, 5, 100, 100)
        fails(Code.SENDER_BELOW_RENT) { SolanaAccountPreflight.requireTokenRent(106, 5, 100, 100) }
        fails(Code.INSUFFICIENT_BALANCE) { SolanaAccountPreflight.requireTokenRent(104, 5, 100, 100) }
    }

    private fun fails(code: Code, action: () -> Unit) =
        assertEquals(code, assertThrows(SolanaAccountPreflightException::class.java, action).code)

    private fun account(data: ByteArray, owner: String = SPL) =
        SolanaRpcAccountInfo(owner, 2039280, false, Base64.getEncoder().encodeToString(data))

    private fun tokenData() = ByteArray(165).also {
        SolanaPublicKeyCodec.decode(MINT).copyInto(it)
        SolanaPublicKeyCodec.decode(WALLET).copyInto(it, 32)
        (64..71).forEach { index -> it[index] = -1 }
        it[108] = 1
    }

    private companion object {
        const val WALLET = "AKnL4NNf3DGWZJS6cPknBuEGnVsV4A4m5tgebLHaRSZ9"
        const val MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SYSTEM = SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS
        const val SPL = SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
        const val TOKEN2022 = SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS
    }
}
