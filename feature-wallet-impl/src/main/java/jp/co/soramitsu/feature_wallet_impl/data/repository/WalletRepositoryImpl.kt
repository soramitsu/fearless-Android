package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.PriceInfo
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapNodeToOperation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationLocalToOperation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationToOperationLocalDb
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.SubQueryOperationsApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.getSubQueryPath
import jp.co.soramitsu.feature_wallet_impl.data.storage.TransferCursorStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Locale
import kotlin.time.ExperimentalTime

class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val walletOperationsApi: SubQueryOperationsApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val assetCache: AssetCache,
    private val walletConstants: WalletConstants,
    private val phishingAddressDao: PhishingAddressDao,
    private val cursorStorage: TransferCursorStorage,
    private val coingeckoApi: CoingeckoApi
) : WalletRepository {

    override fun assetsFlow(accountAddress: String): Flow<List<Asset>> {
        return assetCache.observeAssets(accountAddress)
            .mapList(::mapAssetLocalToAsset)
    }

    override suspend fun syncAssetsRates(account: WalletAccount) = coroutineScope {
        val networkType = account.network.type
        val priceStats = getAssetPriceCoingecko(networkType)

        updateAssetRates(account, priceStats)
    }

    override fun assetFlow(accountAddress: String, type: Token.Type): Flow<Asset> {
        return assetCache.observeAsset(accountAddress, mapTokenTypeToTokenTypeLocal(type))
            .map { mapAssetLocalToAsset(it) }
    }

    override suspend fun getAsset(accountAddress: String, type: Token.Type): Asset? {
        val assetLocal = assetCache.getAsset(accountAddress, mapTokenTypeToTokenTypeLocal(type))

        return assetLocal?.let(::mapAssetLocalToAsset)
    }

    override suspend fun syncAsset(account: WalletAccount, type: Token.Type) {
        return syncAssetsRates(account)
    }

    override fun operationsFirstPageFlow(currentAccount: WalletAccount): Flow<CursorPage<Operation>> {
        return operationDao.observe(currentAccount.address)
            .mapList(::mapOperationLocalToOperation)
            .mapLatest { operations ->
                val cursor = cursorStorage.awaitCursor(currentAccount.address)

                CursorPage(cursor, operations)
            }
    }

    @ExperimentalTime
    override suspend fun syncOperationsFirstPage(
        pageSize: Int,
        filters: Set<TransactionFilter>,
        account: WalletAccount,
    ) {
        val page = getOperations(pageSize, cursor = null, filters, account)
        val accountAddress = account.address

        val elements = page.map { mapOperationToOperationLocalDb(it, OperationLocal.Source.SUBQUERY) }

        operationDao.insertFromSubquery(accountAddress, elements)
        cursorStorage.saveCursor(account.address, page.nextCursor)
    }

    @ExperimentalTime
    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        currentAccount: WalletAccount,
    ): CursorPage<Operation> {
        return withContext(Dispatchers.Default) {
            val path = currentAccount.address.networkType().getSubQueryPath()

            val response = walletOperationsApi.getOperationsHistory(
                path,
                SubqueryHistoryRequest(
                    currentAccount.address,
                    pageSize,
                    cursor,
                    filters
                )
            ).data.query

            val pageInfo = response.historyElements.pageInfo

            val tokenType = Token.Type.fromNetworkType(currentAccount.network.type)

            val operations = response.historyElements.nodes.map {

                mapNodeToOperation(it, tokenType)
            }

            CursorPage(pageInfo.endCursor, operations)
        }
    }

    override suspend fun getContacts(account: WalletAccount, query: String): Set<String> {
        return operationDao.getContacts(query, account.address).toSet()
    }

    override suspend fun getTransferFee(accountAddress: String, transfer: Transfer): Fee {
        val feeRemote = substrateSource.getTransferFee(accountAddress, transfer)

        return mapFeeRemoteToFee(feeRemote, transfer)
    }

    override suspend fun performTransfer(accountAddress: String, transfer: Transfer, fee: BigDecimal) {
        val operationHash = substrateSource.performTransfer(accountAddress, transfer)

        val operation = createOperation(operationHash, transfer, accountAddress, fee, OperationLocal.Source.APP)

        operationDao.insert(operation)
    }

    override suspend fun checkTransferValidity(accountAddress: String, transfer: Transfer): TransferValidityStatus {
        val feeResponse = getTransferFee(accountAddress, transfer)

        val tokenType = transfer.tokenType

        val recipientInfo = substrateSource.getAccountInfo(transfer.recipient)
        val totalRecipientBalanceInPlanks = recipientInfo.totalBalance
        val totalRecipientBalance = tokenType.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetCache.getAsset(accountAddress, mapTokenTypeToTokenTypeLocal(transfer.tokenType))!!
        val asset = mapAssetLocalToAsset(assetLocal)

        val existentialDepositInPlanks = walletConstants.existentialDeposit()
        val existentialDeposit = tokenType.amountFromPlanks(existentialDepositInPlanks)

        return transfer.validityStatus(asset.transferable, asset.total, feeResponse.feeAmount, totalRecipientBalance, existentialDeposit)
    }

    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val publicKeys = phishingApi.getPhishingAddresses().values.flatten()
            .map { it.toAccountId().toHexString(withPrefix = true) }

        val phishingAddressesLocal = publicKeys.map(::PhishingAddressLocal)

        phishingAddressDao.clearTable()
        phishingAddressDao.insert(phishingAddressesLocal)
    }

    override suspend fun isAddressFromPhishingList(address: String) = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingAddressDao.getAllAddresses()

        val addressPublicKey = address.toAccountId().toHexString(withPrefix = true)

        phishingAddresses.contains(addressPublicKey)
    }

    override suspend fun getAccountFreeBalance(accountAddress: String) =
        substrateSource.getAccountInfo(accountAddress).data.free

    private fun createOperation(hash: String, transfer: Transfer, senderAddress: String, fee: BigDecimal, source: OperationLocal.Source) =
        OperationLocal.manualTransfer(
            hash = hash,
            accountAddress = senderAddress,
            tokenType = mapTokenTypeToTokenTypeLocal(transfer.tokenType),
            amount = transfer.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = transfer.recipient,
            fee = transfer.tokenType.planksFromAmount(fee),
            status = OperationLocal.Status.PENDING,
            source = source
        )

    private suspend fun updateAssetRates(
        account: WalletAccount,
        priceStats: Map<String, PriceInfo>?,
    ) = assetCache.updateToken(account.address.networkType()) { cached ->
        val network = account.address.networkType().toString().toLowerCase(Locale.ROOT)

        val price = priceStats?.get(network)?.price

        val change = priceStats?.get(network)?.rateChange

        cached.copy(
            dollarRate = price,
            recentRateChange = change
        )
    }

    private suspend fun getAssetPriceCoingecko(networkType: Node.NetworkType): Map<String, PriceInfo> {
        return apiCall { coingeckoApi.getAssetPrice(networkType.toString(), "usd", true) }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)
}
