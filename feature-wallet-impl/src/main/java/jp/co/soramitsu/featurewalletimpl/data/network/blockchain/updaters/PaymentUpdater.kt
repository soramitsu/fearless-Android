package jp.co.soramitsu.featurewalletimpl.data.network.blockchain.updaters

import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.featureaccountapi.domain.model.accountId
import jp.co.soramitsu.featureaccountapi.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.featurewalletapi.data.cache.AssetCache
import jp.co.soramitsu.featurewalletapi.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.featurewalletapi.data.cache.bindOrmlTokensAccountDataOrDefault
import jp.co.soramitsu.featurewalletapi.data.cache.updateAsset
import jp.co.soramitsu.featurewalletapi.domain.model.Operation
import jp.co.soramitsu.featurewalletimpl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.featurewalletimpl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.featurewalletimpl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.isOrml
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

class PaymentUpdaterFactory(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val operationDao: OperationDao,
    private val chainRegistry: ChainRegistry,
    private val scope: AccountUpdateScope,
    private val updatesMixin: UpdatesMixin
) {

    fun create(chainId: ChainId): Updater {
        return PaymentUpdater(
            substrateSource,
            assetCache,
            operationDao,
            chainRegistry,
            scope,
            chainId,
            updatesMixin
        )
    }
}

class PaymentUpdater(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val operationDao: OperationDao,
    private val chainRegistry: ChainRegistry,
    override val scope: AccountUpdateScope,
    private val chainId: ChainId,
    private val updatesMixin: UpdatesMixin
) : Updater, UpdatesProviderUi by updatesMixin {

    override val requiredModules: List<String> = listOf(Modules.SYSTEM)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val chain = chainRegistry.getChain(chainId)

        val metaAccount = scope.getAccount()
        val chainAccount = metaAccount.chainAccounts[chainId]

        val runtime = chainRegistry.getRuntime(chainId)
        val accountIdsToCheck = listOfNotNull(
            metaAccount.accountId(chain),
            chainAccount?.accountId
        )

        if (accountIdsToCheck.isEmpty()) return emptyFlow()

        return accountIdsToCheck.map { accountId ->
            updatesMixin.startUpdateAsset(metaAccount.id, chainId, accountId, chain.utilityAsset.symbol)

            val key = when {
                chainId.isOrml() -> {
                    val symbol = chain.utilityAsset.symbol
                    runtime.metadata.tokens().storage("Accounts")
                        .storageKey(runtime, accountId, DictEnum.Entry("Token", DictEnum.Entry(symbol, null)))
                }
                else -> runtime.metadata.system().storage("Account").storageKey(runtime, accountId)
            }

            storageSubscriptionBuilder.subscribe(key)
                .onEach { change ->
                    when {
                        chainId.isOrml() -> {
                            val ormlTokensAccountData = bindOrmlTokensAccountDataOrDefault(change.value, runtime)

                            assetCache.updateAsset(metaAccount.id, accountId, chain.utilityAsset) {
                                it.copy(
                                    accountId = accountId,
                                    freeInPlanks = ormlTokensAccountData.free,
                                    miscFrozenInPlanks = ormlTokensAccountData.frozen,
                                    reservedInPlanks = ormlTokensAccountData.reserved
                                )
                            }
                        }
                        else -> {
                            val newAccountInfo = bindAccountInfoOrDefault(change.value, runtime)
                            assetCache.updateAsset(metaAccount.id, accountId, chain.utilityAsset, newAccountInfo)
                        }
                    }

                    fetchTransfers(change.block, chain, accountId)
                }
        }.merge().noSideAffects()
    }

    private suspend fun fetchTransfers(blockHash: String, chain: Chain, accountId: AccountId) {
        val result = substrateSource.fetchAccountTransfersInBlock(chainId, blockHash, accountId)

        val blockTransfers = result.getOrNull() ?: return

        val local = blockTransfers.map {
            val localStatus = when (it.statusEvent) {
                ExtrinsicStatusEvent.SUCCESS -> Operation.Status.COMPLETED
                ExtrinsicStatusEvent.FAILURE -> Operation.Status.FAILED
                null -> Operation.Status.PENDING
            }

            createTransferOperationLocal(it.extrinsic, localStatus, accountId, chain)
        }

        operationDao.insertAll(local)
    }

    private suspend fun createTransferOperationLocal(
        extrinsic: TransferExtrinsic,
        status: Operation.Status,
        accountId: ByteArray,
        chain: Chain
    ): OperationLocal {
        val localCopy = operationDao.getOperation(extrinsic.hash)

        val fee = localCopy?.fee

        val senderAddress = chain.addressOf(extrinsic.senderId)
        val recipientAddress = chain.addressOf(extrinsic.recipientId)

        return OperationLocal.manualTransfer(
            hash = extrinsic.hash,
            chainId = chain.id,
            address = chain.addressOf(accountId),
            chainAssetId = chain.utilityAsset.id, // TODO do not hardcode chain asset id
            amount = extrinsic.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = recipientAddress,
            fee = fee,
            status = mapOperationStatusToOperationLocalStatus(status),
            source = OperationLocal.Source.BLOCKCHAIN
        )
    }
}
