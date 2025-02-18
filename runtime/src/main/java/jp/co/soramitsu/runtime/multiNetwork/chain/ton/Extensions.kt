package jp.co.soramitsu.runtime.multiNetwork.chain.ton

import android.util.Base64
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.utils.Punycode
import jp.co.soramitsu.shared_utils.extensions.toHexString
import org.ton.bigint.BigInt
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.block.MsgAddressInt
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellBuilder.Companion.beginCell
import org.ton.cell.CellSlice
import org.ton.crypto.SecureRandom
import org.ton.tlb.loadTlb
import java.math.BigInteger
import kotlin.math.floor


private val DIGITS = "0123456789abcdef".toCharArray()


fun base64(input: String): ByteArray? {
    return try {
        Base64.decode(input, Base64.DEFAULT)
    } catch (e: Throwable) {
        null
    }
}

fun base64(input: ByteArray): String? {
    return try {
        Base64.encodeToString(input, Base64.DEFAULT)
    } catch (e: Throwable) {
        null
    }
}

fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        val b = byte.toInt() and 0xFF
        append(DIGITS[b shr 4])
        append(DIGITS[b and 0x0F])
    }
}

fun String.hex(): ByteArray {
    val len = length
    if (len % 2 != 0) {
        throw IllegalArgumentException("Invalid hex string")
    }
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

/// ==== Extentions begin
fun String.cellFromBase64(): Cell {
    val parsed = bocFromBase64()
    if (parsed.roots.size != 1) {
        throw IllegalArgumentException("Deserialized more than one cell")
    }
    return parsed.first()
}

fun String.bocFromBase64(): BagOfCells {
    if (startsWith("{")) {
        throw IllegalArgumentException("js objects are not supported")
    }
    return BagOfCells(decodeBase64())
}

fun String.decodeBase64(): ByteArray {
    // force non-url safe base64
    val replaced = replace('-', '+').replace('_', '/')
    /*
    val replaced = trim()
        .replace('-', '+')
        .replace('_', '/')
    val paddedLength = (4 - replaced.length % 4) % 4
    val paddedString = replaced + "=".repeat(paddedLength)
    return paddedString.base64DecodedBytes*/
    return replaced.base64DecodedBytes
}

/**
 * Decode a Base64 standard encoded [String] to [ByteArray].
 *
 * See [RFC 4648 §4](https://datatracker.ietf.org/doc/html/rfc4648#section-4)
 */
val String.base64DecodedBytes: ByteArray
    get() = decodeInternal(Encoding.Standard).map { it.toByte() }.toList().dropLast(count { it == '=' }).toByteArray()

fun String.decodeInternal(encoding: Encoding): Sequence<Int> {
    val padLength = when (length % 4) {
        1 -> 3
        2 -> 2
        3 -> 1
        else -> 0
    }
    return padEnd(length + padLength, '=')
        .replace("=", "A")
        .chunkedSequence(4) {
            (encoding.alphabet.indexOf(it[0]) shl 18) + (encoding.alphabet.indexOf(it[1]) shl 12) +
                    (encoding.alphabet.indexOf(it[2]) shl 6) + encoding.alphabet.indexOf(it[3])
        }
        .map { sequenceOf(0xFF.and(it shr 16), 0xFF.and(it shr 8), 0xFF.and(it)) }
        .flatten()
}

sealed interface Encoding {
    val alphabet: String
    val requiresPadding: Boolean

    data object Standard : Encoding {
        override val alphabet: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        override val requiresPadding: Boolean = true
    }

    data object UrlSafe : Encoding {
        override val alphabet: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        override val requiresPadding: Boolean = false // Padding is optional
    }
}

fun AddrStd.isBounceable(): Boolean {
    return toString(userFriendly = true).isBounceable()
}
fun String.isBounceable(): Boolean {
    return startsWith("0:") || startsWith("E")
}


enum class TonNetwork(
    val value: Int
) {
    MAINNET(-239),
    TESTNET(-3),
}

enum class TONOpCode(val code: Long) {
    UNKNOWN(0),
    OUT_ACTION_SEND_MSG_TAG(0x0ec3c86d),
    SIGNED_EXTERNAL(0x7369676e),
    SIGNED_INTERNAL(0x73696e74),
    JETTON_TRANSFER(0xf8a7ea5),
    NFT_TRANSFER(0x5fcc3d14),
    STONFI_SWAP(0x25938561),
    STONFI_SWAP_V2(0x6664de2a),
    CHANGE_DNS_RECORD(0x4eb1f0f9),
    LIQUID_TF_DEPOSIT(0x47d54391),
    LIQUID_TF_BURN(0x595f07bc),
    WHALES_DEPOSIT(2077040623),
    WHALES_WITHDRAW(3665837821),
    GASLESS(0x878da6e3),
    BATTERY_PAYLOAD(0xb7b2515f),
}

fun CellSlice.loadOpCode(): TONOpCode {
    val code = loadUInt32()
    val long = code.toLong()
    return TONOpCode.entries.firstOrNull { it.code == long } ?: TONOpCode.UNKNOWN
}

fun isBounce(query: String, account: TonAccountData): Boolean {
    if (account.status != AccountStatus.active && query.startsWith("EQ")) {
        return false
    }
    val bounce = query.startsWith("EQ") || !query.startsWith("U")
    if (!query.isValidTonAddress()) {
        return !account.isWallet
    }
    return bounce
}

fun String.isValidTonAddress(): Boolean {
    return try {
        AddrStd(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun newWalletQueryId(): BigInteger {
    return try {
        val randomBytes = SecureRandom.nextBytes(8)
        val hexString = randomBytes.toHexString(true)
        BigInteger(hexString, 16)
    } catch (e: Throwable) {
        BigInteger.ZERO
    }
}

fun getCommentForwardPayload(
    comment: String?
): Cell? {
    return if (comment.isNullOrBlank()) {
        return null
    } else {
        beginCell()
            .storeUInt(0, 32)
            .storeStringTail(comment)
            .endCell()
    }
}

fun String.unicodeToPunycode(): String {
    return try {
        Punycode.encode(this) ?: throw IllegalArgumentException("Invalid punycode")
    } catch (e: Exception) {
        this
    }
}

fun CellBuilder.storeMaybeRef(value: Cell?) = apply {
    if (value == null) {
        storeBit(false)
    } else {
        storeBit(true)
        storeRef(value)
    }
}

fun CellBuilder.storeStringTail(src: String) = apply {
    writeBytes(src.toByteArray(), this)
}

fun writeBytes(src: ByteArray, builder: CellBuilder) {
    if (src.isNotEmpty()) {
        val bytes = floor(builder.availableBits / 8f).toInt()
        if (src.size > bytes) {
            val a = src.copyOfRange(0, bytes)
            val t = src.copyOfRange(bytes, src.size)
            builder.storeBytes(a)
            val bb = beginCell()
            writeBytes(t, bb)
            builder.storeRef(bb.endCell())
        } else {
            builder.storeBytes(src)
        }
    }
}

val CellBuilder.availableBits: Int
    get() = 1023 - bits.size


fun CellSlice.loadMaybeRef(): Cell? {
    if (!loadBit()) {
        return null
    }
    return loadRef()
}

fun CellSlice.loadMaybeAddress(): MsgAddress? {
    return when (val type = preloadUInt(2)) {
        BigInt.valueOf(2) -> loadAddress()
        BigInt.valueOf(0) -> {
            bitsPosition += 2
            null
        }
        else -> throw RuntimeException("Invalid address type: $type")
    }
}

fun CellSlice.loadAddress(): MsgAddressInt {
    return loadTlb(MsgAddressInt)

}

fun CellSlice.loadCoins(): Coins {
    return loadTlb(Coins)
}


fun CellBuilder.storeOpCode(opCode: TONOpCode) = apply {
    storeUInt(opCode.code, 32)
}

fun CellBuilder.storeSeqAndValidUntil(seqNo: Int, validUntil: Long) = apply {
    if (seqNo == 0) {
        for (i in 0 until 32) {
            storeBit(true)
        }
    } else {
        storeUInt(validUntil, 32)
    }
    storeUInt(seqNo, 32)
}


fun CellBuilder.storeQueryId(value: BigInteger) = apply {
    storeUInt(value, 64)
}

fun String.cellFromHex(): Cell {
    val parsed = bocFromHex()
    if (parsed.roots.size != 1) {
        throw IllegalArgumentException("Deserialized more than one cell")
    }
    return parsed.first()
}

fun String.bocFromHex(): BagOfCells {
    if (startsWith("{")) {
        throw IllegalArgumentException("js objects are not supported")
    }
    return BagOfCells(org.ton.crypto.hex(this))
}
