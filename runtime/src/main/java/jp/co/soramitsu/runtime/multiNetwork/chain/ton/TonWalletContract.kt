package jp.co.soramitsu.runtime.multiNetwork.chain.ton

import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.Either
import org.ton.block.Maybe
import org.ton.block.MessageRelaxed
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
            val info = CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
                ihrDisabled = true,
                bounce = transfer.bounceable,
                bounced = false,
                src = AddrNone,
                dest = transfer.destination,
                value = transfer.coins,
                ihrFee = Coins(),
                fwdFee = Coins(),
                createdLt = 0u,
                createdAt = 0u
            )
            val init = Maybe.of(transfer.stateInit?.let {
                Either.of<StateInit, CellRef<StateInit>>(it, null)
            })
            val body = if (transfer.body == null) {
                Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
            } else {
                Either.of<Cell, CellRef<Cell>>(null, CellRef(transfer.body!!))
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
        SmartContract.address(workchain, stateInit)
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