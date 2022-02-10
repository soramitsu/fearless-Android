@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EventRecord
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.Phase
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindExtrinsicStatusEventRecords
import jp.co.soramitsu.common.data.network.runtime.binding.bindOrNull
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.transfer
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings.bindTransferExtrinsic
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isOrml
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import java.math.BigInteger

class WssSubstrateSource(
    private val rpcCalls: RpcCalls,
    private val remoteStorageSource: StorageDataSource,
    private val extrinsicService: ExtrinsicService,
) : SubstrateRemoteSource {

    override suspend fun getAccountInfo(
        chainId: ChainId,
        accountId: AccountId,
    ): AccountInfo {
        return remoteStorageSource.query(
            chainId = chainId,
            keyBuilder = {
                it.metadata.system().storage("Account").storageKey(it, accountId)
            },
            binding = { scale, runtime ->
                scale?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty()
            }
        )
    }

    override suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): BigInteger {
        return extrinsicService.estimateFee(
            chain = chain,
            useBatchAll = batchAll,
            formExtrinsic = {
                transfer(chain, transfer, this.runtime.typeRegistry)
                additional?.invoke(this)
            }
        )
    }

    override suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): String {
        return extrinsicService.submitExtrinsic(
            chain = chain,
            accountId = accountId,
            useBatchAll = batchAll,
            formExtrinsic = {
                transfer(chain, transfer, this.runtime.typeRegistry)
                additional?.invoke(this)
            },
        ).getOrThrow()
    }

    override suspend fun fetchAccountTransfersInBlock(
        chainId: ChainId,
        blockHash: String,
        accountId: ByteArray,
    ): Result<List<TransferExtrinsicWithStatus>> = runCatching {
        val block = rpcCalls.getBlock(chainId, blockHash)

        val extrinsics = remoteStorageSource.queryNonNull(
            chainId = chainId,
            keyBuilder = { it.metadata.system().storage("Events").storageKey() },
            binding = { scale, runtime ->
                val statuses = bindExtrinsicStatusEventRecords(scale, runtime)
                    .filter { it.phase is Phase.ApplyExtrinsic }
                    .associateBy { (it.phase as Phase.ApplyExtrinsic).extrinsicId.toInt() }

                buildExtrinsics(runtime, statuses, block.block.extrinsics)
            },
            at = blockHash
        )

        extrinsics.filter { transferWithStatus ->
            val extrinsic = transferWithStatus.extrinsic

            extrinsic.senderId.contentEquals(accountId) || extrinsic.recipientId.contentEquals(accountId)
        }
    }

    private fun buildExtrinsics(
        runtime: RuntimeSnapshot,
        statuses: Map<Int, EventRecord<ExtrinsicStatusEvent>>,
        extrinsicsRaw: List<String>,
    ): List<TransferExtrinsicWithStatus> {
        return extrinsicsRaw.mapIndexed { index, extrinsicScale ->
            val transferExtrinsic = bindOrNull { bindTransferExtrinsic(extrinsicScale, runtime) }

            transferExtrinsic?.let {
                val status = statuses[index]?.event

                TransferExtrinsicWithStatus(transferExtrinsic, status)
            }
        }.filterNotNull()
    }

    private fun ExtrinsicBuilder.transfer(chain: Chain, transfer: Transfer, typeRegistry: TypeRegistry): ExtrinsicBuilder {
        return when {
            chain.id.isOrml() -> {
                val symbol = chain.utilityAsset.symbol

                call(
                    moduleName = "Tokens",
                    callName = "transfer",
                    arguments = mapOf(
                        "dest" to chain.accountIdOf(transfer.recipient),
                        "currency_id" to DictEnum.Entry("Token", DictEnum.Entry(symbol, null)),
                        "amount" to transfer.amountInPlanks
                    )
                )
            }
            typeRegistry["Address"] is FixedByteArray -> call(
                moduleName = "Balances",
                callName = "transfer",
                arguments = mapOf(
                    "dest" to chain.accountIdOf(transfer.recipient),
                    "value" to transfer.amountInPlanks
                )
            )
            else -> transfer(
                recipientAccountId = chain.accountIdOf(transfer.recipient),
                amount = transfer.amountInPlanks
            )
        }
    }
}
