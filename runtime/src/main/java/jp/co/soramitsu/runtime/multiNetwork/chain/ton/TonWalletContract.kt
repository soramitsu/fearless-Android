package jp.co.soramitsu.runtime.multiNetwork.chain.ton

import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.Either
import org.ton.block.Maybe
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressExt
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.SmartContract
import org.ton.contract.wallet.WalletTransfer
import org.ton.tlb.CellRef
import org.ton.tlb.storeTlb

abstract class TonWalletContract(val workchain: Int = DEFAULT_WORKCHAIN) {
    companion object {
        const val DEFAULT_WORKCHAIN = 0
        const val DEFAULT_WALLET_ID: Int = 698983191

        fun createIntMsg(transfer: WalletTransfer): MessageRelaxed<Cell> {
            val info = when (val dest = transfer.destination) {
                is MsgAddressInt -> {
                    CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
                        ihrDisabled = true,
                        bounce = transfer.bounceable,
                        bounced = false,
                        src = AddrNone,
                        dest = dest,
                        value = transfer.coins,
                        ihrFee = Coins(),
                        fwdFee = Coins(),
                        createdLt = 0u,
                        createdAt = 0u
                    )
                }
                is MsgAddressExt -> {
                    CommonMsgInfoRelaxed.ExtOutMsgInfoRelaxed(
                        src = AddrNone,
                        dest = dest,
                        createdLt = 0u,
                        createdAt = 0u
                    )
                }
            }

            val init = Maybe.of(transfer.messageData.stateInit?.let {
                Either.of<StateInit, CellRef<StateInit>>(null, it)
            })

            val bodyCell = transfer.messageData.body
            val body = if (bodyCell.isEmpty()) {
                Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
            } else {
                Either.of<Cell, CellRef<Cell>>(null, CellRef(bodyCell))
            }

            return MessageRelaxed(
                info = info,
                init = init,
                body = body,
            )
        }
    }

    val walletId = DEFAULT_WALLET_ID + workchain

    val stateInit: StateInit by lazy {
        val cell = getStateCell()
        val code = getCode()
        StateInit(code, cell)
    }

    val address: AddrStd by lazy {
        val stateInitRef = CellRef(stateInit, StateInit)
        val hash = stateInitRef.hash()
        AddrStd(workchain, hash)
    }

    fun stateInitCell(): Cell {
        return CellBuilder.createCell {
            storeTlb(StateInit.tlbCodec(), stateInit)
        }
    }

    abstract val maxMessages: Int

    abstract fun getStateCell(): Cell

    abstract fun getCode(): Cell

    abstract fun getSmartContractAddress(): AddrStd
    abstract fun getAddress(isTestnet: Boolean): String
    abstract fun getAccountId(isTestnet: Boolean): String
}