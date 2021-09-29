package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.math.BigInteger

class TransferExtrinsicWithStatus(
    val extrinsic: TransferExtrinsic,
    val statusEvent: ExtrinsicStatusEvent?,
)

interface SubstrateRemoteSource {

    suspend fun getAccountInfo(
        chainId: ChainId,
        accountId: AccountId
    ): AccountInfo

    suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer
    ): BigInteger

    suspend fun performTransfer(
        accountId: ByteArray,
        chain: Chain,
        transfer: Transfer,
    ): String

    suspend fun fetchAccountTransfersInBlock(
        chainId: ChainId,
        blockHash: String,
        accountId: ByteArray
    ): Result<List<TransferExtrinsicWithStatus>>
}
