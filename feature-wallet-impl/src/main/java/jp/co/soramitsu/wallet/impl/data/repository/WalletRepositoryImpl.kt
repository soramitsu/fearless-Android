package jp.co.soramitsu.wallet.impl.data.repository

import android.os.Build
import com.opencsv.CSVReaderHeaderAware
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain.ExternalApi.Section.Type
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapNodeToOperation
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationLocalToOperation
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationToOperationLocalDb
import jp.co.soramitsu.wallet.impl.data.mappers.toOperation
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.model.request.GiantsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Asset.Companion.createEmpty
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Fee
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryResult
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWalletFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.lang.Integer.min
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val walletOperationsApi: OperationsHistoryApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val assetCache: AssetCache,
    private val walletConstants: WalletConstants,
    private val phishingDao: PhishingDao,
    private val cursorStorage: TransferCursorStorage,
    private val coingeckoApi: CoingeckoApi,
    private val chainRegistry: ChainRegistry,
    private val availableFiatCurrencies: GetAvailableFiatCurrencies,
    private val updatesMixin: UpdatesMixin,
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val soramitsuNetworkClient: SoramitsuNetworkClient,
    private val soraSubqueryFactory: SubQueryClientForSoraWalletFactory
) : WalletRepository, UpdatesProviderUi by updatesMixin {

    private val giantsquidDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", Locale.getDefault()) }

    override fun assetsFlow(meta: MetaAccount): Flow<List<AssetWithStatus>> {
        return combine(
            chainRegistry.chainsById,
            assetCache.observeAssets(meta.id)
        ) { chainsById, assetsLocal ->
            val chainAccounts = meta.chainAccounts.values.toList()
            val updatedAssets = assetsLocal.mapNotNull { asset ->
                mapAssetLocalToAsset(chainsById, asset)?.let {
                    val hasChainAccount = asset.asset.chainId in chainAccounts.mapNotNull { it.chain?.id }
                    AssetWithStatus(
                        asset = it,
                        hasAccount = !it.accountId.contentEquals(emptyAccountIdValue),
                        hasChainAccount = hasChainAccount
                    )
                }
            }

            val assetsByChain: List<AssetWithStatus> = chainsById.values
                .flatMap { chain ->
                    chain.assets.map {
                        AssetWithStatus(
                            asset = createEmpty(
                                chainAsset = it,
                                metaId = meta.id,
                                accountId = meta.accountId(chain) ?: emptyAccountIdValue,
                                minSupportedVersion = chain.minSupportedVersion,
                                enabled = chain.nodes.isNotEmpty()
                            ),
                            hasAccount = !chain.isEthereumBased || meta.ethereumPublicKey != null,
                            hasChainAccount = chain.id in chainAccounts.mapNotNull { it.chain?.id }
                        )
                    }
                }

            val assetsByUniqueAccounts = chainAccounts
                .mapNotNull { chainAccount ->
                    createEmpty(chainAccount)?.let { asset ->
                        AssetWithStatus(
                            asset = asset,
                            hasAccount = true,
                            hasChainAccount = false
                        )
                    }
                }

            val notUpdatedAssetsByUniqueAccounts = assetsByUniqueAccounts.filter { unique ->
                !updatedAssets.any {
                    it.asset.token.configuration.chainToSymbol == unique.asset.token.configuration.chainToSymbol &&
                        it.asset.accountId.contentEquals(unique.asset.accountId)
                }
            }
            val notUpdatedAssets = assetsByChain.filter {
                it.asset.token.configuration.chainToSymbol !in updatedAssets.map { it.asset.token.configuration.chainToSymbol }
            }

            updatedAssets + notUpdatedAssetsByUniqueAccounts + notUpdatedAssets
        }
    }

    override suspend fun getAssets(metaId: Long): List<Asset> = withContext(Dispatchers.Default) {
        val chainsById = chainRegistry.chainsById.first()
        val assetsLocal = assetCache.getAssets(metaId)

        assetsLocal.mapNotNull {
            mapAssetLocalToAsset(chainsById, it)
        }
    }

    private fun mapAssetLocalToAsset(
        chainsById: Map<ChainId, Chain>,
        assetLocal: AssetWithToken
    ): Asset? {
        val (chain, chainAsset) = try {
            val chain = chainsById.getValue(assetLocal.asset.chainId)
            val asset = chain.assetsById.getValue(assetLocal.asset.id)
            chain to asset
        } catch (e: Exception) {
            return null
        }

        return mapAssetLocalToAsset(assetLocal, chainAsset, chain.minSupportedVersion)
    }

    override suspend fun syncAssetsRates(currencyId: String) {
        val chains = chainRegistry.currentChains.first()
        val priceIds = chains.map { it.assets.mapNotNull { it.priceId } }.flatten().toSet()
        val priceStats = getAssetPriceCoingecko(*priceIds.toTypedArray(), currencyId = currencyId)

        updatesMixin.startUpdateTokens(priceIds)

        priceIds.forEach { priceId ->
            val stat = priceStats[priceId] ?: return@forEach
            val price = stat[currencyId]
            val changeKey = "${currencyId}_24h_change"
            val change = stat[changeKey]
            val fiatCurrency = availableFiatCurrencies[currencyId]

            updateAssetRates(priceId, fiatCurrency?.symbol, price, change)
        }
        updatesMixin.finishUpdateTokens(priceIds)
    }

    override fun assetFlow(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Flow<Asset> {
        return assetCache.observeAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)
            .mapNotNull { it }
            .mapNotNull { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
    }

    override suspend fun getAsset(metaId: Long, accountId: AccountId, chainAsset: Chain.Asset, minSupportedVersion: String?): Asset? {
        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)

        return assetLocal?.let { mapAssetLocalToAsset(it, chainAsset, minSupportedVersion) }
    }

    override suspend fun updateAssetHidden(
        metaId: Long,
        accountId: AccountId,
        isHidden: Boolean,
        chainAsset: Chain.Asset
    ) {
        val updateItems = listOf(
            AssetUpdateItem(
                metaId = metaId,
                chainId = chainAsset.chainId,
                accountId = accountId,
                id = chainAsset.id,
                sortIndex = Int.MAX_VALUE, // Int.MAX_VALUE on sorting because we don't use it anymore - just random value
                enabled = !isHidden,
                tokenPriceId = chainAsset.priceId
            )
        )

        assetCache.updateAsset(updateItems)
    }

    private fun AssetWithToken.toAssetUpdateItem() = AssetUpdateItem(
        metaId = asset.metaId,
        chainId = asset.chainId,
        accountId = asset.accountId,
        id = asset.id,
        sortIndex = asset.sortIndex,
        enabled = asset.enabled,
        tokenPriceId = token?.priceId
    )

    override suspend fun syncOperationsFirstPage(
        pageSize: Int,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    ) {
        val accountAddress = chain.addressOf(accountId)
        val page = kotlin.runCatching {
            getOperations(pageSize, cursor = null, filters, accountId, chain, chainAsset)
        }.getOrDefault(CursorPage(null, emptyList()))

        val elements = page.map { mapOperationToOperationLocalDb(it, OperationLocal.Source.SUBQUERY) }

        operationDao.insertFromSubquery(accountAddress, chain.id, chainAsset.id, elements)
        cursorStorage.saveCursor(chain.id, chainAsset.id, accountId, page.nextCursor)

        if (elements.isEmpty() && chainAsset.isUtility.not()) {
            syncOperationsFirstPage(pageSize, filters, accountId, chain, chain.utilityAsset)
        }
    }

    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    ): CursorPage<Operation> {
        return withContext(Dispatchers.Default) {
            val historyUrl = chain.externalApi?.history?.url
            val historyType = chain.externalApi?.history?.type

            if (historyUrl == null || historyType?.isHistory() != true) {
                throw HistoryNotSupportedException()
            }
            if (historyType in listOf(Type.GIANTSQUID, Type.SUBSQUID) && chainAsset.isUtility.not()) {
                throw HistoryNotSupportedException()
            }

            val accountAddress = chain.addressOf(accountId)
            when (historyType) {
                Type.SORA -> {
                    val soraStartPage = 1L
                    val page = cursor?.toLongOrNull() ?: soraStartPage
                    val soraOperations = getSoraOperationsHistory(historyUrl, pageSize, page, chain, chainAsset, accountAddress, filters)
                    return@withContext CursorPage(page.inc().toString(), soraOperations)
                }
                Type.SUBQUERY -> {
                    return@withContext getSubqueryHistoryOperations(historyUrl, pageSize, accountAddress, chain, chainAsset, filters, cursor)
                }
                Type.SUBSQUID -> {
                    val page = cursor?.toIntOrNull() ?: 0
                    val operations = getSubsquidHistoryOperations(historyUrl, accountAddress, pageSize, page, chainAsset, filters)
                    return@withContext CursorPage(page.inc().toString(), operations)
                }
                Type.GIANTSQUID -> {
                    val page = 0
                    val operations = getGiantsquidHistoryOperations(historyUrl, accountAddress, pageSize, page, chainAsset, filters)
                    return@withContext CursorPage(null, operations)
                }
                else -> {
                    return@withContext CursorPage(null, emptyList<Operation>())
                }
            }
        }
    }

    private fun TransactionFilter.isAppliedOrNull(filters: Collection<TransactionFilter>) = when {
        this in filters -> true
        else -> null
    }

    private suspend fun getSubsquidHistoryOperations(
        historyUrl: String,
        accountAddress: String,
        pageSize: Int,
        page: Int,
        chainAsset: Chain.Asset,
        filters: Set<TransactionFilter>
    ): List<Operation> {
        val response = walletOperationsApi.getSubsquidOperationsHistory(
            url = historyUrl,
            SubsquidHistoryRequest(
                accountAddress = accountAddress,
                pageSize,
                pageSize * page
            )
        )

        val operations = response.data.historyElements.map {
            val transfer = TransactionFilter.TRANSFER.isAppliedOrNull(filters)?.let { transferApplied ->
                it.transfer?.let { transfer ->
                    Operation(
                        id = it.extrinsicIdx ?: it.id,
                        address = it.address,
                        time = it.timestamp,
                        chainAsset = chainAsset,
                        type = Operation.Type.Transfer(
                            hash = it.extrinsicHash,
                            myAddress = accountAddress,
                            amount = transfer.amount.toBigIntegerOrNull().orZero(),
                            receiver = transfer.to,
                            sender = transfer.from,
                            status = Operation.Status.fromSuccess(transfer.success),
                            fee = transfer.fee
                        )
                    )
                }
            }
            val reward = TransactionFilter.REWARD.isAppliedOrNull(filters)?.let { rewardApplied ->
                it.reward?.let { reward ->
                    Operation(
                        id = it.id,
                        address = it.address,
                        time = it.timestamp,
                        chainAsset = chainAsset,
                        type = Operation.Type.Reward(
                            amount = reward.amount.toBigIntegerOrNull().orZero(),
                            isReward = reward.isReward,
                            era = reward.era ?: 0,
                            validator = reward.validator
                        )
                    )
                }
            }
            val extrinsic = TransactionFilter.EXTRINSIC.isAppliedOrNull(filters)?.let { extrinsicApplied ->
                it.extrinsic?.let { extrinsic ->
                    Operation(
                        id = it.id,
                        address = it.address,
                        time = it.timestamp,
                        chainAsset = chainAsset,
                        type = Operation.Type.Extrinsic(
                            hash = extrinsic.hash,
                            module = extrinsic.module,
                            call = extrinsic.call,
                            fee = extrinsic.fee.toBigIntegerOrNull().orZero(),
                            status = Operation.Status.fromSuccess(extrinsic.success)
                        )
                    )
                }
            }
            listOfNotNull(transfer, reward, extrinsic)
        }.flatten()
        return operations
    }

    private suspend fun getGiantsquidHistoryOperations(
        historyUrl: String,
        accountAddress: String,
        pageSize: Int,
        page: Int,
        chainAsset: Chain.Asset,
        filters: Set<TransactionFilter>
    ): List<Operation> {
        val response = walletOperationsApi.getGiantsquidOperationsHistory(
            url = historyUrl,
            GiantsquidHistoryRequest(
                accountAddress = accountAddress,
                pageSize,
                pageSize * page
            )
        )

        val transfers = filters.firstOrNull { it == TransactionFilter.TRANSFER }?.let {
            response.data.transfers?.map { transfer ->
                Operation(
                    id = transfer.id,
                    address = accountAddress,
                    time = parseTimeToMillis(transfer.transfer.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = transfer.transfer.extrinsicHash,
                        myAddress = accountAddress,
                        amount = transfer.transfer.amount.toBigIntegerOrNull().orZero(),
                        receiver = transfer.transfer.to?.id.orEmpty(),
                        sender = transfer.transfer.from?.id.orEmpty(),
                        status = Operation.Status.fromSuccess(transfer.transfer.success == true),
                        fee = BigInteger.ZERO
                    )
                )
            }
        }.orEmpty()

        val rewards = filters.firstOrNull { it == TransactionFilter.REWARD }?.let {
            response.data.rewards?.map { reward ->
                Operation(
                    id = reward.id,
                    address = accountAddress,
                    time = parseTimeToMillis(reward.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Reward(
                        amount = reward.amount.toBigIntegerOrNull().orZero(),
                        isReward = true,
                        era = reward.era.orZero().toInt(),
                        validator = reward.validatorId
                    )
                )
            }
        }.orEmpty()

        if (TransactionFilter.EXTRINSIC in filters) {
            // todo complete history parse
            response.data.slashes?.map { slash ->
                Operation(
                    id = slash.id,
                    address = accountAddress,
                    time = parseTimeToMillis(slash.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Extrinsic(
                        hash = "",
                        module = "slash",
                        call = "",
                        fee = BigInteger.ZERO,
                        status = Operation.Status.COMPLETED
                    )
                )
            }
            response.data.bonds?.map { bond ->
                Operation(
                    id = bond.id,
                    address = accountAddress,
                    time = parseTimeToMillis(bond.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Extrinsic(
                        hash = bond.extrinsicHash.orEmpty(),
                        module = "bond",
                        call = bond.amount,
                        fee = BigInteger.ZERO,
                        status = Operation.Status.fromSuccess(bond.success == true)
                    )
                )
            }
        }

        return transfers + rewards
    }

    private fun parseTimeToMillis(timestamp: String): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.parse(timestamp).toEpochMilli()
        } else {
            try {
                giantsquidDateFormat.parse(timestamp)?.time ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }

    private suspend fun getSubqueryHistoryOperations(
        historyUrl: String,
        pageSize: Int,
        accountAddress: String,
        chain: Chain,
        chainAsset: Chain.Asset,
        filters: Set<TransactionFilter>,
        cursor: String?
    ): CursorPage<Operation> {
        val requestRewards = chainAsset.staking != Chain.Asset.StakingType.UNSUPPORTED
        val response = walletOperationsApi.getOperationsHistory(
            url = historyUrl,
            SubqueryHistoryRequest(
                accountAddress = accountAddress,
                pageSize,
                cursor,
                filters,
                requestRewards
            )
        ).data.query

        val encodedCurrencyId = if (!chainAsset.isUtility) {
            val runtime = chainRegistry.getRuntime(chain.id)
            val currencyIdKey = runtime.typeRegistry.types.keys.find { it.contains("CurrencyId") }
            val currencyIdType = runtime.typeRegistry.types[currencyIdKey]

            useScaleWriter {
                val currency = chainAsset.currency as? DictEnum.Entry<*> ?: return@useScaleWriter
                val alias = (currencyIdType?.value as Alias)
                val currencyIdEnum = alias.aliasedReference.requireValue() as DictEnum
                currencyIdEnum.encode(this, runtime, currency)
            }.toHexString(true)
        } else {
            null
        }

        val pageInfo = response.historyElements.pageInfo
        val filteredOperations = if (chainAsset.isUtility) {
            response.historyElements.nodes.filter {
                it.transfer?.assetId == null &&
                    it.extrinsic?.assetId == null &&
                    it.reward?.assetId == null
            }
        } else {
            response.historyElements.nodes.filter {
                it.transfer?.assetId == encodedCurrencyId ||
                    it.extrinsic?.assetId == encodedCurrencyId ||
                    it.reward?.assetId == encodedCurrencyId
            }
        }

        val operations = filteredOperations.map { mapNodeToOperation(it, chainAsset) }

        return CursorPage(pageInfo.endCursor, operations)
    }

    private suspend fun getSoraOperationsHistory(
        historyUrl: String,
        pageSize: Int,
        page: Long,
        chain: Chain,
        chainAsset: Chain.Asset,
        accountAddress: String,
        filters: Set<TransactionFilter>
    ): List<Operation> {
        val subQueryClientForSora = soraSubqueryFactory.create(soramitsuNetworkClient, historyUrl, pageSize)

        val soraHistory: TxHistoryResult<TxHistoryItem> = subQueryClientForSora.getTransactionHistoryPaged(
            accountAddress,
            chain.name,
            page
        )

        val soraHistoryItems: List<TxHistoryItem> = soraHistory.items
        return soraHistoryItems.mapNotNull { it.toOperation(chain, chainAsset, accountAddress, filters) }
    }

    override fun operationsFirstPageFlow(
        accountId: AccountId,
        chain: Chain,
        chainAsset: Chain.Asset
    ): Flow<CursorPage<Operation>> {
        val accountAddress = chain.addressOf(accountId)

        return operationDao.observe(accountAddress, chain.id, chainAsset.id)
            .mapList {
                mapOperationLocalToOperation(it, chainAsset, chain)
            }
            .mapLatest { operations ->
                val cursor = cursorStorage.awaitCursor(chain.id, chainAsset.id, accountId)

                CursorPage(cursor, operations)
            }
    }

    override fun getOperationAddressWithChainIdFlow(limit: Int?, chainId: ChainId): Flow<Set<String>> {
        return operationDao.observeOperations(chainId).mapList { operation ->
            val accountAddress = currentAccountAddress.invoke(chainId)
            if (operation.address == accountAddress) {
                val receiver = when (operation.receiver) {
                    null, accountAddress -> null
                    else -> operation.receiver
                }
                val sender = when (operation.sender) {
                    null, accountAddress -> null
                    else -> operation.sender
                }
                receiver ?: sender
            } else {
                null
            }
        }
            .map {
                val nonNullList = it.filterNotNull()
                when {
                    limit == null || limit < 0 -> nonNullList
                    else -> nonNullList.subList(0, min(limit, nonNullList.size))
                }.toSet()
            }
    }

    override suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): Fee {
        val fee = substrateSource.getTransferFee(chain, transfer, additional, batchAll)

        return Fee(
            transferAmount = transfer.amount,
            feeAmount = chain.utilityAsset.amountFromPlanks(fee)
        )
    }

    override suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        fee: BigDecimal,
        tip: BigInteger?,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): String {
        val operationHash = substrateSource.performTransfer(accountId, chain, transfer, tip, additional, batchAll)
        val accountAddress = chain.addressOf(accountId)

        val operation = createOperation(
            operationHash,
            transfer,
            accountAddress,
            fee,
            OperationLocal.Source.APP
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
        val feeResponse = getTransferFee(chain, transfer, additional, batchAll)

        val chainAsset = transfer.chainAsset
        val recipientAccountId = chain.accountIdOf(transfer.recipient)

        val totalRecipientBalanceInPlanks = substrateSource.getTotalBalance(chainAsset, recipientAccountId)
        val totalRecipientBalance = chainAsset.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chainAsset.id)!!
        val asset = mapAssetLocalToAsset(assetLocal, chainAsset, chain.minSupportedVersion)

        val existentialDepositInPlanks = walletConstants.existentialDeposit(chainAsset).orZero()
        val existentialDeposit = chainAsset.amountFromPlanks(existentialDepositInPlanks)

        val utilityAssetLocal = assetCache.getAsset(metaId, accountId, chainAsset.chainId, chain.utilityAsset.id)!!
        val utilityAsset = mapAssetLocalToAsset(utilityAssetLocal, chain.utilityAsset, chain.minSupportedVersion)

        val utilityExistentialDepositInPlanks = walletConstants.existentialDeposit(chain.utilityAsset).orZero()
        val utilityExistentialDeposit = chain.utilityAsset.amountFromPlanks(utilityExistentialDepositInPlanks)

        val tipInPlanks = kotlin.runCatching { walletConstants.tip(chain.id) }.getOrNull()
        val tip = tipInPlanks?.let { chain.utilityAsset.amountFromPlanks(it) }

        return transfer.validityStatus(
            senderTransferable = asset.transferable,
            senderTotal = asset.total.orZero(),
            fee = feeResponse.feeAmount,
            recipientBalance = totalRecipientBalance,
            existentialDeposit = existentialDeposit,
            isUtilityToken = chainAsset.isUtility,
            senderUtilityBalance = utilityAsset.total.orZero(),
            utilityExistentialDeposit = utilityExistentialDeposit,
            tip = tip
        )
    }

    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingApi.getPhishingAddresses()
        val phishingLocal = CSVReaderHeaderAware(phishingAddresses.byteStream().bufferedReader()).mapNotNull {
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

    override suspend fun isAddressFromPhishingList(address: String) = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingDao.getAllAddresses().map { it.lowercase() }

        phishingAddresses.contains(address.lowercase())
    }

    override suspend fun getPhishingInfo(address: String): PhishingLocal? {
        return phishingDao.getPhishingInfo(address)
    }

    override suspend fun getAccountFreeBalance(chainAsset: Chain.Asset, accountId: AccountId) =
        substrateSource.getAccountFreeBalance(chainAsset, accountId)

    override suspend fun getEquilibriumAssetRates(chainAsset: Chain.Asset) =
        substrateSource.getEquilibriumAssetRates(chainAsset)

    override suspend fun getEquilibriumAccountInfo(asset: Chain.Asset, accountId: AccountId) =
        substrateSource.getEquilibriumAccountInfo(asset, accountId)

    override suspend fun updateAssets(newItems: List<AssetUpdateItem>) {
        assetCache.updateAsset(newItems)
    }

    private fun createOperation(
        hash: String,
        transfer: Transfer,
        senderAddress: String,
        fee: BigDecimal,
        source: OperationLocal.Source
    ) =
        OperationLocal.manualTransfer(
            hash = hash,
            address = senderAddress,
            chainAssetId = transfer.chainAsset.id,
            chainId = transfer.chainAsset.chainId,
            amount = transfer.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = transfer.recipient,
            fee = transfer.chainAsset.planksFromAmount(fee),
            status = OperationLocal.Status.PENDING,
            source = source
        )

    private suspend fun updateAssetRates(
        priceId: String,
        fiatSymbol: String?,
        price: BigDecimal?,
        change: BigDecimal?
    ) = assetCache.updateTokenPrice(priceId) { cached ->
        cached.copy(
            fiatRate = price,
            fiatSymbol = fiatSymbol,
            recentRateChange = change
        )
    }

    override suspend fun getSingleAssetPriceCoingecko(priceId: String, currency: String): BigDecimal? {
        return apiCall {
            coingeckoApi.getSingleAssetPrice(priceIds = priceId, currency = currency)
        }.getOrDefault(priceId, null)?.getOrDefault(currency, null)?.toBigDecimal()
    }

    private suspend fun getAssetPriceCoingecko(vararg priceId: String, currencyId: String): Map<String, Map<String, BigDecimal>> {
        return apiCall { coingeckoApi.getAssetPrice(priceId.joinToString(","), currencyId, true) }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)

    override suspend fun getRemoteConfig(): Result<AppConfigRemote> {
        return kotlin.runCatching { remoteConfigFetcher.getAppConfig() }
    }

    override fun chainRegistrySyncUp() {
        chainRegistry.syncUp()
    }
}
