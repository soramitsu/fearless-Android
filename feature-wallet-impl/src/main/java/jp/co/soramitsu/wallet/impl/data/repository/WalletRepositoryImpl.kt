package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import com.opencsv.CSVReaderHeaderAware
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindString
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.isCallMovedToAssetHub
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.impl.data.mappers.mapCachedAssetToAsset
import jp.co.soramitsu.wallet.impl.data.mappers.toDomainAssets
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.TransferServiceProvider
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val ethereumSource: EthereumRemoteSource,
    private val operationDao: OperationDao,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val assetDao: AssetDao,
    private val walletConstants: WalletConstants,
    private val phishingDao: PhishingDao,
    private val coingeckoApi: CoingeckoApi,
    private val chainRegistry: ChainRegistry,
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val extrinsicService: ExtrinsicService,
    private val remoteStorageSource: StorageDataSource,
    private val pricesSyncService: PricesSyncService,
    private val transferServiceProvider: TransferServiceProvider,
    private val tonRemoteSource: TonRemoteSource,
    private val portfolioVisibility: PortfolioVisibility,
    private val currentAccountAssetReader: CurrentAccountAssetReader
) : WalletRepository {

    companion object {
        private const val COINGECKO_REQUEST_DELAY_MILLIS = 60 * 1000
    }

    private val coingeckoCache = mutableMapOf<String, MutableMap<String, Pair<Long, BigDecimal>>>()

    override fun assetsFlow(meta: MetaAccount): Flow<List<AssetWithStatus>> =
        currentAccountAssetReader.observeAccountSnapshots(meta.id).map { state ->
            val snapshot = (state as? SelectedAccountAssetSnapshot.Ready)?.snapshot
            if (snapshot == null || snapshot.context.meta.id != meta.id) return@map emptyList()
            val chainAccounts = snapshot.context.meta.chainAccounts.keys
            snapshot.toDomainAssets(portfolioVisibility::hasTrustedPrice).map { asset ->
                AssetWithStatus(
                    asset = asset,
                    hasAccount = asset.accountId.isNotEmpty(),
                    hasChainAccount = asset.chainId in chainAccounts
                )
            }
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(emptyList())
            throw failure
        }

    override suspend fun getAssets(metaId: Long): List<Asset> = withContext(Dispatchers.Default) {
        val snapshot = currentAccountAssetReader.getSnapshot(metaId)
        require(snapshot.context.meta.id == metaId) { "Wallet snapshot identity mismatch" }
        snapshot.toDomainAssets(portfolioVisibility::hasTrustedPrice)
    }

    /** The requested account must still be owned by this exact captured wallet/catalog. */
    private fun snapshotAsset(
        snapshot: CurrentAccountAssetSnapshot,
        metaId: Long,
        accountId: AccountId,
        chainId: ChainId,
        assetId: String
    ): Asset? {
        if (snapshot.context.meta.id != metaId) return null
        val chain = snapshot.chainsById[chainId] ?: return null
        val configuration = chain.assetsById[assetId] ?: return null
        if (!accountId.contentEquals(snapshot.context.meta.accountId(chain))) return null
        val row = snapshot.assets.firstOrNull {
            it.asset.metaId == metaId && it.asset.chainId == chainId && it.asset.id == assetId &&
                it.asset.accountId.contentEquals(accountId)
        } ?: snapshot.assets.firstOrNull {
            it.asset.metaId == metaId && it.asset.chainId == chainId && it.asset.id == assetId && it.asset.accountId.isEmpty()
        } ?: return null
        val admitted = if (row.asset.accountId.isEmpty()) row.withRequestedAccount(accountId) else row
        return mapCachedAssetToAsset(admitted, configuration, chain.minSupportedVersion,
            portfolioVisibility.hasTrustedPrice(configuration), snapshot.context.fiat)
    }

    override suspend fun syncAssetsRates(currencyId: String) {
        pricesSyncService.sync()
    }

    override fun assetFlow(
        metaId: Long,
        accountId: AccountId,
        chainAsset: CoreAsset,
        minSupportedVersion: String?
    ): Flow<Asset> {
        val capturedAccountId = accountId.copyOf()
        // The non-null historical API revokes removed/loading values with an all-unknown asset.
        // It never invents a cached zero or retains the previous selected currency's quote.
        val unavailable = Asset.createEmpty(chainAsset, metaId, capturedAccountId,
            minSupportedVersion = minSupportedVersion)
        return currentAccountAssetReader.observeAccountSnapshots(metaId).map { state ->
            val snapshot = (state as? SelectedAccountAssetSnapshot.Ready)?.snapshot
            snapshot?.let { snapshotAsset(it, metaId, capturedAccountId, chainAsset.chainId, chainAsset.id) } ?: unavailable
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(unavailable)
            throw failure
        }.distinctUntilChanged()
    }

    override suspend fun getAsset(
        metaId: Long,
        accountId: AccountId,
        chainAsset: CoreAsset,
        minSupportedVersion: String?
    ): Asset? {
        val capturedAccountId = accountId.copyOf()
        return snapshotAsset(currentAccountAssetReader.getSnapshot(metaId), metaId,
            capturedAccountId, chainAsset.chainId, chainAsset.id)
    }

    override suspend fun updateAssetsHidden(state: List<AssetUpdateItem>) {
        assetDao.updateOrInsertAssetPreferences(state)
    }

    override suspend fun getTransferMaxAmount(chain: Chain, transfer: Transfer): BigDecimal =
        transferServiceProvider.provide(chain).getTransferMaxAmount(transfer)

    override suspend fun observeTransferFee(
        chain: Chain,
        transfer: Transfer
    ): Flow<BigDecimal> {
        val transferServiceProvider =  transferServiceProvider.provide(chain)
        return transferServiceProvider.observeTransferFee(transfer)
    }

    override suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): BigDecimal {
        return transferServiceProvider.provide(chain).getTransferFee(transfer)
    }

    override suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): String {
        val transferService = transferServiceProvider.provide(chain)
        val operationHash = transferService.transfer(transfer)

        val accountAddress = chain.addressOf(accountId)
        val utilityAsset = chain.assets.firstOrNull { it.isUtility }

        if(chain.ecosystem == Ecosystem.Ton) return operationHash

        val operation = createOperation(
            operationHash,
            transfer,
            accountAddress,
            OperationLocal.Source.APP,
            utilityAsset
        )

        operationDao.insert(operation)
        return operationHash
    }

    override suspend fun checkTransferValidity(
        metaId: Long,
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): TransferValidityStatus {
        if (chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana()) {
            // The protocol service revalidates the displayed quote and current RPC/UTXO state before signing.
            // Substrate balance/ED lookups are neither meaningful nor authoritative for these chains.
            return jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel.Ok
        }
        val capturedAccountId = accountId.copyOf()
        require(transfer.chainAsset.chainId == chain.id) { "Transfer asset belongs to another chain" }
        val feeResponse = getTransferFee(chain, transfer, additional, batchAll)

        val chainAsset = transfer.chainAsset

        val totalRecipientBalanceInPlanks = getTotalBalance(chainAsset, chain, transfer.recipient)
        val totalRecipientBalance = chainAsset.amountFromPlanks(totalRecipientBalanceInPlanks)

        val existentialDepositInPlanks = walletConstants.existentialDeposit(chainAsset).orZero()
        val existentialDeposit = chainAsset.amountFromPlanks(existentialDepositInPlanks)
        val utilityExistentialDepositInPlanks = chain.utilityAsset?.let { walletConstants.existentialDeposit(it) }.orZero()
        val utilityExistentialDeposit = chain.utilityAsset?.amountFromPlanks(utilityExistentialDepositInPlanks).orZero()

        val tipInPlanks = try {
            walletConstants.tip(chain.id)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        }
        val tip = tipInPlanks?.let { chain.utilityAsset?.amountFromPlanks(it) }

        // Capture both balances together after all remote/constant awaits. A changed account or
        // catalog cannot authorize the old transfer configuration or borrow a sibling's quantity.
        val snapshot = currentAccountAssetReader.getSnapshot(metaId)
        val capturedChain = requireNotNull(snapshot.chainsById[chain.id]) { "Transfer chain is unavailable" }
        require(capturedChain.assetsById[chainAsset.id] == chainAsset) { "Transfer asset configuration changed" }
        require(capturedChain.utilityAsset == chain.utilityAsset) { "Transfer utility configuration changed" }
        val asset = requireNotNull(snapshotAsset(snapshot, metaId, capturedAccountId, chain.id, chainAsset.id)) {
            "Transfer account balance is unavailable"
        }
        val senderTransferable = requireNotNull(asset.transferable) { "Transferable balance is unknown" }
        val senderTotal = requireNotNull(asset.total) { "Total balance is unknown" }
        val senderUtilityBalance = if (chainAsset.isUtility) senderTotal else {
            val utility = requireNotNull(capturedChain.utilityAsset) { "Utility asset is unavailable" }
            requireNotNull(snapshotAsset(snapshot, metaId, capturedAccountId, chain.id, utility.id)?.total) {
                "Utility balance is unknown"
            }
        }

        return transfer.validityStatus(
            senderTransferable = senderTransferable,
            senderTotal = senderTotal,
            fee = feeResponse,
            recipientBalance = totalRecipientBalance,
            existentialDeposit = existentialDeposit,
            isUtilityToken = chainAsset.isUtility,
            senderUtilityBalance = senderUtilityBalance,
            utilityExistentialDeposit = utilityExistentialDeposit,
            tip = tip
        )
    }

    override suspend fun getTotalBalance(
        chainAsset: CoreAsset,
        chain: Chain,
        address: String
    ): BigInteger {
        return when(chain.ecosystem) {
            Ecosystem.Substrate,
            Ecosystem.EthereumBased -> {
                val accountId = chain.accountIdOf(address)
                substrateSource.getTotalBalance(chainAsset, accountId)
            }
            Ecosystem.Ethereum -> {
                val accountId = chain.accountIdOf(address)
                ethereumSource.getTotalBalance(chainAsset, chain, accountId)
                    .requireValue() // handle errors
            }
            Ecosystem.Ton -> {
                BigInteger.ONE
                //throw IllegalStateException("getTotalBalance for ton is not implemented")
            }
        }
    }

    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingApi.getPhishingAddresses()
        val phishingLocal =
            CSVReaderHeaderAware(phishingAddresses.byteStream().bufferedReader()).mapNotNull {
                try {
                    PhishingLocal(
                        name = it[0],
                        address = it[1],
                        type = it[2],
                        subtype = it[3]
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        phishingDao.clearTable()
        phishingDao.insert(phishingLocal)
    }

    override suspend fun isAddressFromPhishingList(address: String) =
        withContext(Dispatchers.Default) {
            val phishingAddresses = phishingDao.getAllAddresses().map { it.lowercase() }

            phishingAddresses.contains(address.lowercase())
        }

    override suspend fun getPhishingInfo(address: String): PhishingLocal? {
        return phishingDao.getPhishingInfo(address)
    }

    override suspend fun getAccountFreeBalance(chainAsset: CoreAsset, accountId: AccountId) =
        substrateSource.getAccountFreeBalance(chainAsset, accountId)

    override suspend fun getAccountSpendableBalance(chainAsset: CoreAsset, accountId: AccountId) =
        substrateSource.getAccountSpendableBalance(chainAsset, accountId)

    override suspend fun getEquilibriumAssetRates(chainAsset: CoreAsset) =
        substrateSource.getEquilibriumAssetRates(chainAsset)

    override suspend fun getEquilibriumAccountInfo(asset: CoreAsset, accountId: AccountId) =
        substrateSource.getEquilibriumAccountInfo(asset, accountId)

    private fun createOperation(
        hash: String,
        transfer: Transfer,
        senderAddress: String,
        source: OperationLocal.Source,
        utilityAsset: CoreAsset?
    ) =
        OperationLocal.manualTransfer(
            hash = hash,
            address = senderAddress,
            chainAssetId = transfer.chainAsset.id,
            chainId = transfer.chainAsset.chainId,
            amount = transfer.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = transfer.recipient,
            fee = transfer.estimateFee?.let { utilityAsset?.planksFromAmount(it) },
            status = OperationLocal.Status.PENDING,
            source = source
        )

    override suspend fun getSingleAssetPriceCoingecko(
        priceId: String,
        currency: String
    ): BigDecimal? {
        coingeckoCache[priceId]?.get(currency)?.let { (cacheUntilMillis, cachedValue) ->
            if (System.currentTimeMillis() <= cacheUntilMillis) {
                return cachedValue
            }
        }
        val apiValue = apiCall {
            coingeckoApi.getSingleAssetPrice(priceIds = priceId, currency = currency)
        }.getOrDefault(priceId, null)?.getOrDefault(currency, null)?.toBigDecimal()

        apiValue?.let {
            val currencyMap = coingeckoCache[priceId] ?: mutableMapOf()
            val cacheUntilMillis = System.currentTimeMillis() + COINGECKO_REQUEST_DELAY_MILLIS
            currencyMap[currency] = cacheUntilMillis to apiValue
            coingeckoCache[priceId] = currencyMap
        }
        return apiValue
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)

    override suspend fun getRemoteConfig(): AppConfigRemote {
        return remoteConfigFetcher.getAppConfig()
    }

    override suspend fun getControllerAccount(chainId: ChainId, accountId: AccountId): AccountId? {
        return substrateSource.getControllerAccount(chainId, accountId)
    }

    override suspend fun getStashAccount(chainId: ChainId, accountId: AccountId): AccountId? {
        return substrateSource.getStashAccount(chainId, accountId)
    }

    override fun observeChainsPerAsset(
        accountMetaId: Long,
        assetId: String
    ): Flow<Map<Chain, Asset?>> = currentAccountAssetReader.observeAccountSnapshots(accountMetaId).map { state ->
        val snapshot = (state as? SelectedAccountAssetSnapshot.Ready)?.snapshot
        if (snapshot == null || snapshot.context.meta.id != accountMetaId) return@map emptyMap()
        // A registry ID which maps to conflicting symbols cannot pick an arbitrary network.
        val symbol = snapshot.chainsById.values.flatMap { it.assets }
            .filter { it.id == assetId }.map { it.symbol.removedXcPrefix() }.distinct().singleOrNull()
            ?: return@map emptyMap()
        snapshot.toDomainAssets(portfolioVisibility::hasTrustedPrice)
            .filter { it.token.configuration.symbol.removedXcPrefix() == symbol &&
                (it.enabled == true || (it.enabled == null && it.total?.signum() == 1)) }
            .groupBy { it.chainId }
            .mapNotNull { (chainId, assets) ->
                val chain = snapshot.chainsById[chainId] ?: return@mapNotNull null
                val selected = assets.sortedWith(compareBy<Asset>(
                    { if (it.token.configuration.symbol == symbol) 0 else 1 }, { it.id }
                )).first()
                chain to selected
            }.toMap()
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(emptyMap())
        throw failure
    }

    override suspend fun getVestingLockedAmount(chainId: ChainId): BigInteger? {
        val currentMetaAccount = accountRepository.getSelectedMetaAccount()
        val chain = chainsRepository.getChain(chainId)
        val accountId = currentMetaAccount.accountId(chain)

        val locksStorageKeyAndReturnType = chainRegistry.getRuntimeOrNull(chainId)?.let { runtime ->
            if (runtime.metadata.moduleOrNull(Modules.BALANCES)?.storage?.get("Locks") != null) {
                runtime.metadata.balances().storage("Locks").storageKey(runtime, accountId) to
                        runtime.metadata.balances().storage("Locks").returnType()
            } else if (runtime.metadata.moduleOrNull(Modules.TOKENS)?.storage?.get("Locks") != null) {
                val currency = chain.utilityAsset?.currency
                runtime.metadata.tokens().storage("Locks")
                    .storageKey(runtime, accountId, currency) to
                        runtime.metadata.tokens().storage("Locks").returnType()
            } else {
                null
            }
        }

        return locksStorageKeyAndReturnType?.let { (locksStorageKey, returnType) ->
            remoteStorageSource.query(
                chainId = chainId,
                keyBuilder = { locksStorageKey },
                binding = { scale, runtime ->
                    bindVestingLockedAmount(scale, runtime, returnType)
                }
            )
        }
    }

    @UseCaseBinding
    fun bindVestingLockedAmount(
        scale: String?,
        runtime: RuntimeSnapshot,
        returnType: Type<*>
    ): BigInteger? {
        scale ?: return null

        val locksDynamicInstance =
            returnType.fromHexOrNull(runtime, scale).cast<List<Struct.Instance>>()

        return locksDynamicInstance.firstOrNull {
            bindString(it["id"]) == "ormlvest"
        }?.let {
            bindNumber(it["amount"])
        }
    }

    override suspend fun estimateClaimRewardsFee(chainId: ChainId): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = chainsRepository.getChain(chainId)
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain)

            if (accountId == null) {
                BigInteger.ZERO
            } else {
                extrinsicService.estimateFee(chain, accountId) {
                    claimRewards()
                }
            }
        }
    }

    override suspend fun claimRewards(chain: IChain, accountId: AccountId): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(chain, accountId) {
                claimRewards()
            }
        }
    }

}

private val CLAIM_REWARDS_CALLS = listOf(
    Modules.VESTING to "claim",
    Modules.VESTING to "vest",
    Modules.VESTED_REWARDS to "claim_rewards"
)

/**
 * Module and call that claim vested rewards on this runtime, or null when there is none to offer.
 * Polkadot and Kusama still list Vesting.vest after the Asset Hub migration but reject it at dispatch after charging
 * the fee, and fearless-utils refuses to build it, so such calls are skipped.
 */
fun RuntimeMetadata.claimRewardsCall(): Pair<String, String>? = CLAIM_REWARDS_CALLS.firstOrNull { (moduleName, callName) ->
    moduleOrNull(moduleName)?.calls?.get(callName) != null && !isCallMovedToAssetHub(moduleName, callName)
}

fun ExtrinsicBuilder.claimRewards(): ExtrinsicBuilder {
    val (moduleName, callName) = runtime.metadata.claimRewardsCall() ?: return this

    return call(moduleName, callName, emptyMap())
}
