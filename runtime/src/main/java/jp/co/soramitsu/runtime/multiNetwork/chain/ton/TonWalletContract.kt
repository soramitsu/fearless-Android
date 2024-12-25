package jp.co.soramitsu.runtime.multiNetwork.chain.ton

import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.SmartContract
import org.ton.tlb.storeTlb

abstract class TonWalletContract(val workchain: Int = DEFAULT_WORKCHAIN) {
    companion object {
        const val DEFAULT_WORKCHAIN = 0
        const val DEFAULT_WALLET_ID: Int = 698983191
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