package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.data.network.solana.SolanaAccountPreflightException.Code
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.math.BigInteger
import java.util.Base64

/** Validates signing-capable wallet recipients and exact SPL layouts before composing a payment. */
object SolanaAccountPreflight {
    /** A wallet recipient must be a real signing key and absent or a plain system-owned account. */
    fun requireWallet(address: String, account: SolanaRpcAccountInfo?) {
        val publicKey = try { SolanaPublicKeyCodec.decode(address) } catch (_: IllegalArgumentException) {
            throw SolanaAccountPreflightException(Code.RECIPIENT_CANNOT_SIGN)
        }
        // This is signature-key validation, deliberately different from PDA point decompression.
        if (!Ed25519.validatePublicKeyFull(publicKey, 0)) fail(Code.RECIPIENT_CANNOT_SIGN)
        if (account != null && (account.ownerProgram != SolanaTransferTransactionBuilder.SYSTEM_PROGRAM_ADDRESS ||
                account.executable || account.dataBase64.isNotEmpty())) fail(Code.RECIPIENT_IS_NOT_WALLET)
    }

    /** Checks an initialized standard SPL mint and binds its decimals to the reviewed asset. */
    fun requireMint(account: SolanaRpcAccountInfo?, decimals: Int) {
        val bytes = tokenData(account, 82, Code.INVALID_TOKEN_MINT)
        if (decimals !in 0..255 || bytes[44].toInt() and 255 != decimals || bytes[45] != 1.toByte() ||
            !validOption(bytes, 0) || !validOption(bytes, 46)) fail(Code.INVALID_TOKEN_MINT)
    }

    /** Binds a standard SPL account to its exact mint, wallet owner and initialized spendable state. */
    fun tokenBalance(account: SolanaRpcAccountInfo?, mint: String, wallet: String): BigInteger {
        val bytes = tokenData(account, 165, Code.INVALID_TOKEN_ACCOUNT)
        val expectedMint = SolanaPublicKeyCodec.decode(mint)
        val expectedWallet = SolanaPublicKeyCodec.decode(wallet)
        if (!bytes.copyOfRange(0, 32).contentEquals(expectedMint) || !bytes.copyOfRange(32, 64).contentEquals(expectedWallet) ||
            !validOption(bytes, 72) || !validOption(bytes, 109) || !validOption(bytes, 129)) fail(Code.INVALID_TOKEN_ACCOUNT)
        if (bytes[108] == 2.toByte()) fail(Code.TOKEN_ACCOUNT_FROZEN)
        if (bytes[108] != 1.toByte()) fail(Code.INVALID_TOKEN_ACCOUNT)
        return BigInteger(1, bytes.copyOfRange(64, 72).reversedArray())
    }

    /** The recipient must reach rent exemption; the sender must end at zero or remain exempt. */
    fun requireNativeRent(balance: Long, amount: Long, fee: Long, rent: Long, recipientBalance: Long) {
        require(balance >= 0 && amount > 0 && fee >= 0 && rent >= 0 && recipientBalance >= 0)
        val remaining = BigInteger.valueOf(balance).subtract(BigInteger.valueOf(amount)).subtract(BigInteger.valueOf(fee))
        if (remaining.signum() < 0) fail(Code.INSUFFICIENT_BALANCE)
        if (remaining.signum() > 0 && remaining < BigInteger.valueOf(rent)) fail(Code.SENDER_BELOW_RENT)
        if (BigInteger.valueOf(recipientBalance).add(BigInteger.valueOf(amount)) < BigInteger.valueOf(rent)) fail(Code.RECIPIENT_BELOW_RENT)
    }

    /** Account-creation rent is a debit in addition to the network fee for token sends. */
    fun requireTokenRent(balance: Long, fee: Long, creationRent: Long, walletRent: Long) {
        require(balance >= 0 && fee >= 0 && creationRent >= 0 && walletRent >= 0)
        val remaining = BigInteger.valueOf(balance).subtract(BigInteger.valueOf(fee)).subtract(BigInteger.valueOf(creationRent))
        if (remaining.signum() < 0) fail(Code.INSUFFICIENT_BALANCE)
        if (remaining.signum() > 0 && remaining < BigInteger.valueOf(walletRent)) fail(Code.SENDER_BELOW_RENT)
    }

    private fun tokenData(account: SolanaRpcAccountInfo?, length: Int, code: Code): ByteArray {
        if (account == null || account.executable || account.ownerProgram != SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS ||
            account.dataBase64.length > 220) fail(code)
        val bytes = try { Base64.getDecoder().decode(account.dataBase64) } catch (_: IllegalArgumentException) { fail(code) }
        if (bytes.size != length) fail(code)
        return bytes
    }

    private fun validOption(bytes: ByteArray, offset: Int): Boolean = bytes[offset] in 0..1 &&
        bytes[offset + 1] == 0.toByte() && bytes[offset + 2] == 0.toByte() && bytes[offset + 3] == 0.toByte()

    private fun fail(code: Code): Nothing = throw SolanaAccountPreflightException(code)
}
