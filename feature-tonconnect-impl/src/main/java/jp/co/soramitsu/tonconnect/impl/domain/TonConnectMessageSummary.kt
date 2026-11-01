package jp.co.soramitsu.tonconnect.impl.domain

import jp.co.soramitsu.common.utils.readableUtf8Text
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.isBounceable
import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.tlb.loadTlb
import java.math.BigInteger

/** What one message of a `sendTransaction` request does, decoded from the cells that are signed. */
data class TonConnectMessageSummary(
    /** The destination as a user-friendly address, with the bounce flag the dApp wrote. */
    val destination: String,
    val destinationAddress: AddrStd,
    /** The TON the message carries, in nanotons. */
    val amount: BigInteger,
    val payload: TonConnectPayloadSummary,
    /** The message carries a state init: it deploys a contract at its destination. */
    val deploysContract: Boolean
)

sealed interface TonConnectPayloadSummary {
    /** False when the body holds instructions the wallet cannot decode. */
    val isComplete: Boolean

    data object Empty : TonConnectPayloadSummary {
        override val isComplete = true
    }

    data class Comment(val text: String) : TonConnectPayloadSummary {
        override val isComplete = true
    }

    /**
     * A TEP-74 jetton `transfer` sent to the sender's jetton wallet: [amount] jettons, in their
     * smallest units, go to the wallet of [recipient].
     */
    data class JettonTransfer(
        val amount: BigInteger,
        val recipient: AddrStd,
        /** Where the TON left over is returned; null for none. */
        val responseDestination: AddrStd?,
        /** TON the recipient is notified with. */
        val forwardTonAmount: BigInteger,
        val forwardPayload: ForwardPayload,
        val hasCustomPayload: Boolean = false
    ) : TonConnectPayloadSummary {
        override val isComplete: Boolean
            get() = !hasCustomPayload && forwardPayload !is ForwardPayload.Undecoded
    }

    /** A TEP-62 NFT `transfer`: the NFT at the destination goes to [newOwner]. */
    data class NftTransfer(
        val newOwner: AddrStd,
        val responseDestination: AddrStd?,
        val forwardTonAmount: BigInteger,
        val forwardPayload: ForwardPayload,
        val hasCustomPayload: Boolean = false
    ) : TonConnectPayloadSummary {
        override val isComplete: Boolean
            get() = !hasCustomPayload && forwardPayload !is ForwardPayload.Undecoded
    }

    /** A body the wallet cannot decode. [opCode] is its first 32 bits, when it has them. */
    data class Undecoded(val opCode: Long?) : TonConnectPayloadSummary {
        override val isComplete = false
    }

    /** The data a jetton or NFT transfer hands to the recipient. */
    sealed interface ForwardPayload {
        data object Empty : ForwardPayload
        data class Comment(val text: String) : ForwardPayload

        /** Instructions for the recipient contract, such as a swap, that the wallet cannot decode. */
        data object Undecoded : ForwardPayload
    }
}

/** Decodes the message bodies of TON Connect requests. Anything not read exactly stays undecoded. */
object TonConnectMessageSummaries {

    private const val OP_BITS = 32
    private const val QUERY_ID_BITS = 64
    private const val BYTE_BITS = 8
    private const val COMMENT_OP = 0L
    private const val JETTON_TRANSFER_OP = 0x0f8a7ea5L
    private const val NFT_TRANSFER_OP = 0x5fcc3d14L

    fun summary(message: TonConnectRawMessage): TonConnectMessageSummary = TonConnectMessageSummary(
        destination = message.address.toString(
            userFriendly = true,
            urlSafe = true,
            testOnly = false,
            bounceable = message.addressValue.trim().isBounceable()
        ),
        destinationAddress = message.address,
        amount = message.amount.toBigInteger(),
        payload = payload(message.payload),
        deploysContract = !message.stateInitValue.isNullOrBlank()
    )

    /** The body exactly as signed: `TonConnectTransfers` sends [TonConnectRawMessage.payload] unchanged. */
    fun payload(body: Cell): TonConnectPayloadSummary {
        if (body.bits.size == 0 && body.refs.isEmpty()) return TonConnectPayloadSummary.Empty
        val slice = body.beginParse()
        if (slice.remainingBits < OP_BITS) return TonConnectPayloadSummary.Undecoded(null)
        val opCode = slice.loadUInt(OP_BITS).toLong()
        val decoded = runCatching {
            when (opCode) {
                COMMENT_OP -> snakeText(slice)?.let(TonConnectPayloadSummary::Comment)
                JETTON_TRANSFER_OP -> jettonTransfer(slice)
                NFT_TRANSFER_OP -> nftTransfer(slice)
                else -> null
            }
        }.getOrNull()
        return decoded ?: TonConnectPayloadSummary.Undecoded(opCode)
    }

    // transfer#0f8a7ea5 query_id:uint64 amount:(VarUInteger 16) destination:MsgAddress
    //   response_destination:MsgAddress custom_payload:(Maybe ^Cell)
    //   forward_ton_amount:(VarUInteger 16) forward_payload:(Either Cell ^Cell)
    private fun jettonTransfer(slice: CellSlice): TonConnectPayloadSummary? {
        slice.loadUInt(QUERY_ID_BITS)
        val amount = slice.loadTlb(Coins).amount.value
        val recipient = slice.loadTlb(MsgAddress) as? AddrStd ?: return null
        val responseDestination = responseDestination(slice.loadTlb(MsgAddress)) ?: return null
        val hasCustomPayload = slice.loadBit()
        if (hasCustomPayload) slice.loadRef()
        val forwardTonAmount = slice.loadTlb(Coins).amount.value
        return TonConnectPayloadSummary.JettonTransfer(
            amount = amount,
            recipient = recipient,
            responseDestination = responseDestination.address,
            forwardTonAmount = forwardTonAmount,
            forwardPayload = forwardPayload(slice),
            hasCustomPayload = hasCustomPayload
        )
    }

    // transfer#5fcc3d14 query_id:uint64 new_owner:MsgAddress response_destination:MsgAddress
    //   custom_payload:(Maybe ^Cell) forward_amount:(VarUInteger 16) forward_payload:(Either Cell ^Cell)
    private fun nftTransfer(slice: CellSlice): TonConnectPayloadSummary? {
        slice.loadUInt(QUERY_ID_BITS)
        val newOwner = slice.loadTlb(MsgAddress) as? AddrStd ?: return null
        val responseDestination = responseDestination(slice.loadTlb(MsgAddress)) ?: return null
        val hasCustomPayload = slice.loadBit()
        if (hasCustomPayload) slice.loadRef()
        val forwardTonAmount = slice.loadTlb(Coins).amount.value
        return TonConnectPayloadSummary.NftTransfer(
            newOwner = newOwner,
            responseDestination = responseDestination.address,
            forwardTonAmount = forwardTonAmount,
            forwardPayload = forwardPayload(slice),
            hasCustomPayload = hasCustomPayload
        )
    }

    private class ResponseDestination(val address: AddrStd?)

    private fun responseDestination(address: MsgAddress): ResponseDestination? = when (address) {
        is AddrNone -> ResponseDestination(null)
        is AddrStd -> ResponseDestination(address)
        else -> null
    }

    // forward_payload:(Either Cell ^Cell): inline in the rest of this cell, or in its own cell.
    private fun forwardPayload(slice: CellSlice): TonConnectPayloadSummary.ForwardPayload {
        val payload = if (slice.loadBit()) {
            if (slice.remainingBits != 0 || slice.refs.size - slice.refsPosition != 1) {
                return TonConnectPayloadSummary.ForwardPayload.Undecoded
            }
            slice.loadRef().beginParse()
        } else {
            slice
        }
        if (payload.remainingBits == 0 && payload.refs.size == payload.refsPosition) {
            return TonConnectPayloadSummary.ForwardPayload.Empty
        }
        val text = payload.takeIf { it.remainingBits >= OP_BITS && it.loadUInt(OP_BITS).toLong() == COMMENT_OP }
            ?.let(::snakeText)
        return text?.let(TonConnectPayloadSummary.ForwardPayload::Comment) ?: TonConnectPayloadSummary.ForwardPayload.Undecoded
    }

    // Whole-byte snake text continues in the cell's only reference.
    private fun snakeText(slice: CellSlice): String? {
        val bytes = mutableListOf<Byte>()
        var current: CellSlice? = slice
        while (current != null) {
            val remainingRefs = current.refs.size - current.refsPosition
            if (current.remainingBits % BYTE_BITS != 0 || remainingRefs > 1) return null
            bytes += current.loadBits(current.remainingBits).toByteArray().toList()
            current = if (remainingRefs == 1) current.loadRef().beginParse() else null
        }
        return if (bytes.isEmpty()) "" else readableUtf8Text(bytes.toByteArray())
    }
}
