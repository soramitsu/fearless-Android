package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.model.FeeResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings.TransferExtrinsic

class TransferExtrinsicWithStatus(
    val extrinsic: TransferExtrinsic,
    val statusEvent: ExtrinsicStatusEvent?,
)

interface SubstrateRemoteSource {
    suspend fun getAccountInfo(address: String): AccountInfo

    suspend fun getTransferFee(
        accountAddress: String,
        transfer: Transfer,
    ): FeeResponse

    suspend fun performTransfer(
        accountAddress: String,
        transfer: Transfer,
    ): String

    suspend fun fetchAccountTransfersInBlock(
        blockHash: String,
        accountAddress: String,
    ): Result<List<TransferExtrinsicWithStatus>>
}
