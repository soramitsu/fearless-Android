@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EventRecord
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.Phase
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindExtrinsicStatusEventRecords
import jp.co.soramitsu.common.data.network.runtime.binding.bindOrNull
import jp.co.soramitsu.common.data.network.runtime.calls.FeeCalculationRequest
import jp.co.soramitsu.common.data.network.runtime.model.FeeResponse
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.preBinder
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.transfer
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings.bindTransferExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.GetBlockRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.SignedBlock
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.extrinsic.KeypairProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WssSubstrateSource(
    private val socketService: SocketService,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
) : SubstrateRemoteSource {

    override suspend fun getAccountInfo(address: String): AccountInfo {
        val publicKeyBytes = address.toAccountId()
        val request = AccountInfoRequest(publicKeyBytes)

        val response = socketService.executeAsync(request, mapper = pojo<String>())
        val accountInfo = response.result?.let { bindAccountInfo(it, runtimeProperty.get()) }

        return accountInfo ?: AccountInfo.empty()
    }

    override suspend fun getTransferFee(accountAddress: String, transfer: Transfer): FeeResponse {
        val extrinsic = buildTransferExtrinsic(accountAddress, extrinsicBuilderFactory.fakeKeypairProvider(), transfer)

        val request = FeeCalculationRequest(extrinsic)

        return socketService.executeAsync(request, mapper = pojo<FeeResponse>().nonNull())
    }

    override suspend fun performTransfer(
        accountAddress: String,
        transfer: Transfer,
    ): String {
        val extrinsic = buildTransferExtrinsic(accountAddress, extrinsicBuilderFactory.accountKeypairProvider(), transfer)

        return socketService.executeAsync(
            SubmitExtrinsicRequest(extrinsic),
            mapper = pojo<String>().nonNull(),
            deliveryType = DeliveryType.AT_MOST_ONCE
        )
    }

    override suspend fun fetchAccountTransfersInBlock(blockHash: String, accountAddress: String): Result<List<TransferExtrinsicWithStatus>> = runCatching {
        val blockRequest = GetBlockRequest(blockHash)

        val block = socketService.executeAsync(blockRequest, mapper = pojo<SignedBlock>().nonNull())

        val runtime = runtimeProperty.get()

        val eventsKey = runtime.metadata.system().storage("Events").storageKey()
        val eventsRequest = GetStorageRequest(listOf(eventsKey, blockHash))

        val rawResponse = socketService.executeAsync(eventsRequest, mapper = preBinder())

        val statusesByExtrinsicId = bindExtrinsicStatusEventRecords(rawResponse, runtime)
            .filter { it.phase is Phase.ApplyExtrinsic }
            .associateBy { (it.phase as Phase.ApplyExtrinsic).extrinsicId.toInt() }

        filterAccountTransactions(accountAddress, block.block.extrinsics, statusesByExtrinsicId)
    }

    private suspend fun buildTransferExtrinsic(
        originAddress: String,
        keypairProvider: KeypairProvider,
        transfer: Transfer,
    ): String = withContext(Dispatchers.Default) {
        extrinsicBuilderFactory.create(originAddress, keypairProvider)
            .transfer(recipientAccountId = transfer.recipient.toAccountId(), amount = transfer.amountInPlanks)
            .build()
    }

    private suspend fun filterAccountTransactions(
        accountAddress: String,
        extrinsics: List<String>,
        statuesByExtrinsicIndex: Map<Int, EventRecord<ExtrinsicStatusEvent>>,
    ): List<TransferExtrinsicWithStatus> {
        return withContext(Dispatchers.Default) {
            val currentPublicKey = accountAddress.toAccountId()

            extrinsics.mapIndexed { index, hex ->
                val transferExtrinsic = bindOrNull { bindTransferExtrinsic(hex, runtimeProperty.get()) }

                transferExtrinsic?.let {
                    val status = statuesByExtrinsicIndex[index]?.event

                    TransferExtrinsicWithStatus(transferExtrinsic, status)
                }
            }
                .filterNotNull()
                .filter { transferWithStatus ->
                    val extrinsic = transferWithStatus.extrinsic

                    extrinsic.senderId.contentEquals(currentPublicKey) || extrinsic.recipientId.contentEquals(currentPublicKey)
                }
        }
    }
}
