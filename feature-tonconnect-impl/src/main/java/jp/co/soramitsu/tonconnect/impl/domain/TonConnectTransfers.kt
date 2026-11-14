package jp.co.soramitsu.tonconnect.impl.domain

import jp.co.soramitsu.runtime.multiNetwork.chain.ton.TonWalletContract.Companion.createIntMsg
import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import org.ton.block.MessageRelaxed
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef

/** Builds what a TON Connect `sendTransaction` signs, from the request as the user approved it. */
object TonConnectTransfers {

    private const val DEFAULT_SEND_MODE = 3
    private const val UINT32_BITS = 32
    private const val UINT8_BITS = 8
    private const val SIMPLE_SEND_OP = 0

    /**
     * The internal message exactly as requested: destination, amount, payload and
     * state init are the dApp's own. Only the bounce flag is derived by the wallet.
     */
    fun transfer(message: TonConnectRawMessage, bounceable: Boolean): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.destination = message.address
        builder.messageData = MessageData.Raw(message.payload, message.stateInit?.let { CellRef(it) })
        builder.bounceable = bounceable
        builder.coins = message.coins
        return builder.build()
    }

    /**
     * The v4 wallet body to sign. [validUntil] is always stored, also for an
     * undeployed wallet (seqno 0), so a signed message can never stay valid forever.
     */
    fun unsignedBody(
        walletId: Int,
        seqno: Int,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): Cell {
        return CellBuilder.createCell {
            storeUInt(walletId, UINT32_BITS)
            storeUInt(validUntil, UINT32_BITS)
            storeUInt(seqno, UINT32_BITS)
            storeUInt(SIMPLE_SEND_OP, UINT8_BITS)
            for (transfer in transfers) {
                val sendMode = if (transfer.sendMode > -1) transfer.sendMode else DEFAULT_SEND_MODE
                storeUInt(sendMode, UINT8_BITS)
                storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), CellRef(createIntMsg(transfer)))
            }
        }
    }
}
