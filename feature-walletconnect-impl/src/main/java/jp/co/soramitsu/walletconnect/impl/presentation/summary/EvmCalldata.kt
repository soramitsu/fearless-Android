package jp.co.soramitsu.walletconnect.impl.presentation.summary

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.web3j.crypto.Keys
import java.math.BigInteger

/** A token call the wallet can show in words. Calldata in any other shape stays an unknown contract call. */
internal sealed interface EvmTokenCall {
    /** ERC-20 `transfer(address,uint256)`. */
    data class Transfer(val recipient: String, val amount: BigInteger) : EvmTokenCall

    /** ERC-20 `transferFrom(address,address,uint256)`. */
    data class TransferFrom(val owner: String, val recipient: String, val amount: BigInteger) : EvmTokenCall

    /** ERC-20 `approve(address,uint256)`; with [isIncrease] `increaseAllowance(address,uint256)`. */
    data class Approve(val spender: String, val amount: BigInteger, val isIncrease: Boolean = false) : EvmTokenCall

    /** ERC-721/ERC-1155 `setApprovalForAll(address,bool)`. */
    data class ApprovalForAll(val operator: String, val approved: Boolean) : EvmTokenCall
}

/**
 * Reads EVM calldata with strict ABI rules: the exact length of the function's static arguments,
 * addresses with clean upper bytes and booleans that are 0 or 1. Calldata a token contract could
 * read in another way is never given a meaning here.
 */
internal object EvmCalldata {
    private const val SELECTOR_SIZE = 4
    private const val WORD_SIZE = 32
    private const val ADDRESS_SIZE = 20
    private const val TRANSFER_FROM_WORDS = 3

    private const val TRANSFER = "a9059cbb"
    private const val TRANSFER_FROM = "23b872dd"
    private const val APPROVE = "095ea7b3"
    private const val INCREASE_ALLOWANCE = "39509351"
    private const val SET_APPROVAL_FOR_ALL = "a22cb465"

    /** The 4-byte function selector as `0x` hex, or null when [data] is too short to have one. */
    fun selector(data: ByteArray): String? =
        data.takeIf { it.size >= SELECTOR_SIZE }?.copyOfRange(0, SELECTOR_SIZE)?.toHexString(withPrefix = true)

    fun decode(data: ByteArray): EvmTokenCall? {
        val selector = selector(data)?.removePrefix("0x") ?: return null
        val words = words(data) ?: return null
        return when (selector) {
            TRANSFER -> words.ifSize(2) { values -> address(values[0])?.let { EvmTokenCall.Transfer(it, uint(values[1])) } }
            TRANSFER_FROM -> words.ifSize(TRANSFER_FROM_WORDS, ::transferFrom)
            APPROVE -> words.ifSize(2) { values -> address(values[0])?.let { EvmTokenCall.Approve(it, uint(values[1])) } }
            INCREASE_ALLOWANCE -> words.ifSize(2) { values ->
                address(values[0])?.let { EvmTokenCall.Approve(it, uint(values[1]), isIncrease = true) }
            }
            SET_APPROVAL_FOR_ALL -> words.ifSize(2, ::approvalForAll)
            else -> null
        }
    }

    private fun transferFrom(words: List<ByteArray>): EvmTokenCall? {
        val owner = address(words[0]) ?: return null
        val recipient = address(words[1]) ?: return null
        return EvmTokenCall.TransferFrom(owner, recipient, uint(words[2]))
    }

    private fun approvalForAll(words: List<ByteArray>): EvmTokenCall? {
        val operator = address(words[0]) ?: return null
        val approved = bool(words[1]) ?: return null
        return EvmTokenCall.ApprovalForAll(operator, approved)
    }

    private fun words(data: ByteArray): List<ByteArray>? {
        val arguments = data.size - SELECTOR_SIZE
        if (arguments % WORD_SIZE != 0) return null
        return (0 until arguments / WORD_SIZE).map { index ->
            val start = SELECTOR_SIZE + index * WORD_SIZE
            data.copyOfRange(start, start + WORD_SIZE)
        }
    }

    private inline fun <T> List<ByteArray>.ifSize(size: Int, read: (List<ByteArray>) -> T): T? =
        if (this.size == size) read(this) else null

    private fun address(word: ByteArray): String? {
        val padding = word.copyOfRange(0, WORD_SIZE - ADDRESS_SIZE)
        if (padding.any { it != 0.toByte() }) return null
        return checksumAddress(word.copyOfRange(WORD_SIZE - ADDRESS_SIZE, WORD_SIZE).toHexString(withPrefix = true))
    }

    private fun uint(word: ByteArray): BigInteger = BigInteger(1, word)

    private fun bool(word: ByteArray): Boolean? = when (uint(word)) {
        BigInteger.ZERO -> false
        BigInteger.ONE -> true
        else -> null
    }
}

/** An EVM address in its EIP-55 mixed-case form, which is how wallets and explorers show it. */
internal fun checksumAddress(address: String): String = Keys.toChecksumAddress(address)

/** Allowances this large never run out for any real token, so they are shown as unlimited. */
@Suppress("MagicNumber")
internal val unlimitedAllowanceThreshold: BigInteger = BigInteger.ONE.shiftLeft(128)

internal fun BigInteger.isUnlimitedAllowance(): Boolean = this >= unlimitedAllowanceThreshold
