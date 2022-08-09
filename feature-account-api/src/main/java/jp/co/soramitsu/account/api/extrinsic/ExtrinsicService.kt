package jp.co.soramitsu.account.api.extrinsic

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.ExtrinsicStatusResponse
import jp.co.soramitsu.common.data.network.runtime.blake2b256String
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v2.getChainAccountKeypair
import jp.co.soramitsu.common.data.secrets.v2.getMetaAccountKeypair
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.event
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.network.rpc.PhaseRecord
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile

data class BlockEvent(val module: Int, val event: Int, val number: Long?)

class ExtrinsicService(
    private val rpcCalls: RpcCalls,
    private val accountRepository: AccountRepository,
    private val secretStoreV2: SecretStoreV2,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun submitExtrinsic(
        chain: Chain,
        accountId: ByteArray,
        useBatchAll: Boolean = false,
        tip: BigInteger? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val metaAccount = accountRepository.findMetaAccount(accountId) ?: error("No meta account found accessing ${accountId.toHexString()}")
        val keypair = secretStoreV2.getKeypairFor(metaAccount, chain, accountId)

        val extrinsicBuilder = extrinsicBuilderFactory.create(chain, keypair, metaAccount.cryptoType(chain), tip)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build(useBatchAll)

        rpcCalls.submitExtrinsic(chain.id, extrinsic)
    }

    suspend fun estimateFee(
        chain: Chain,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        val extrinsicBuilder = extrinsicBuilderFactory.create(chain)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build(useBatchAll)

        return rpcCalls.getExtrinsicFee(chain.id, extrinsic)
    }

    private suspend fun SecretStoreV2.getKeypairFor(
        metaAccount: MetaAccount,
        chain: Chain,
        accountId: ByteArray
    ): Keypair {
        return if (hasChainSecrets(metaAccount.id, accountId)) {
            getChainAccountKeypair(metaAccount.id, accountId)
        } else {
            getMetaAccountKeypair(metaAccount.id, chain.isEthereumBased)
        }
    }

    suspend fun submitAndWatchExtrinsic(
        chain: Chain,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Pair<String, String>? {
        val extrinsicBuilder = extrinsicBuilderFactory.create(chain)
        extrinsicBuilder.formExtrinsic()
        val extrinsic = extrinsicBuilder.build()
        val result = rpcCalls.submitAndWatchExtrinsic(chain.id, extrinsic)
            .catch {
                emit("" to ExtrinsicStatusResponse.ExtrinsicStatusPending(""))
            }
            .map {
                Triple(
                    it.first,
                    (it.second as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    it.second.subscription
                )
            }
            .transformWhile { value ->
                val finish = value.second?.let { blockHash ->
                    val txHash = value.first
                    val blockResponse = rpcCalls.getBlock(blockHash)
                    val extrinsicId =
                        blockResponse.block.extrinsics.indexOfFirst { s -> s.blake2b256String() == txHash }
                            .toLong()
                    val isSuccess = isExtrinsicSuccessful(chain, extrinsicId, blockHash, txHash)
                    if (isSuccess) txHash to blockHash else null
                }
                emit(finish)
                val more = value.second.isNullOrEmpty() && value.first.isNotEmpty()
                more
            }.first {
                it != null
            }
        return result
    }

    private suspend fun isExtrinsicSuccessful(
        chain: Chain,
        extrinsicId: Long,
        blockHash: String,
        txHash: String
    ): Boolean {
        val events = rpcCalls.getEventsInBlock(chain.id, blockHash)
        val blockEvents = events.map {
            BlockEvent(
                it.event.moduleIndex,
                it.event.eventIndex,
                (it.phase as? PhaseRecord.ApplyExtrinsic)?.extrinsicId?.toLong()
            )
        }
        if (blockEvents.isEmpty()) return false
        val extrinsicBuilder = extrinsicBuilderFactory.create(chain)
        val runtime = extrinsicBuilder.runtime
        val (moduleIndexSuccess, eventIndexSuccess) = runtime.metadata.system().event("ExtrinsicSuccess").index
        val (moduleIndexFailed, eventIndexFailed) = runtime.metadata.system().event("ExtrinsicFailed").index
        val successEvent = blockEvents.find { event ->
            event.module == moduleIndexSuccess && event.event == eventIndexSuccess && event.number == extrinsicId
        }
        val failedEvent = blockEvents.find { event ->
            event.module == moduleIndexFailed && event.event == eventIndexFailed && event.number == extrinsicId
        }
        return when {
            successEvent != null -> {
                true
            }
            failedEvent != null -> {
                false
            }
            else -> {
                false
            }
        }
    }
}
