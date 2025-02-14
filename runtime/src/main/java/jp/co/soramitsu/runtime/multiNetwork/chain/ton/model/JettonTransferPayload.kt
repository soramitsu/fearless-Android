package jp.co.soramitsu.runtime.multiNetwork.chain.ton.model

import jp.co.soramitsu.common.data.network.ton.JettonTransferPayloadRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.cellFromHex
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.tlb.CellRef
import org.ton.tlb.asRef

data class JettonTransferPayload(val tokenAddress: String,
                                 val customPayload: Cell? = null,
                                 val stateInit: CellRef<StateInit>? = null) {

    constructor(tokenAddress: String, model: JettonTransferPayloadRemote) : this(
        tokenAddress = tokenAddress,
        customPayload = model.customPayload?.cellFromHex(),
        stateInit = model.stateInit?.cellFromHex()?.asRef(StateInit),
    )
}