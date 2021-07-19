package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.subscan.SubscanResponse
import jp.co.soramitsu.common.data.network.subscan.subscanSubDomain
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
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
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapNodesToOperation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationLocalToOperation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationToOperationLocalDb
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryHistoryElementByAddressRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal

@Suppress("EXPERIMENTAL_API_USAGE")
class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val subscanApi: SubscanNetworkApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val assetCache: AssetCache,
    private val walletConstants: WalletConstants,
    private val phishingAddressDao: PhishingAddressDao
) : WalletRepository {

    override fun assetsFlow(accountAddress: String): Flow<List<Asset>> {
        return assetCache.observeAssets(accountAddress)
            .mapList(::mapAssetLocalToAsset)
    }

    override suspend fun syncAssetsRates(account: WalletAccount) = coroutineScope {
        val networkType = account.network.type

        val currentPriceStatsDeferred = async { getAssetPrice(networkType, AssetPriceRequest.createForNow()) }
        val yesterdayPriceStatsDeferred = async { getAssetPrice(networkType, AssetPriceRequest.createForYesterday()) }

        updateAssetRates(account, currentPriceStatsDeferred.await(), yesterdayPriceStatsDeferred.await())
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

    override fun operationsFirstPageFlow(currentAccount: WalletAccount, accounts: List<WalletAccount>): Flow<List<Operation>> {
        val accountsByAddress = accounts.associateBy { it.address }

        return operationDao.observe(currentAccount.address).mapList {
            val accountName = defineAccountNameForTransaction(accountsByAddress, displayAddress = it.receiver ?: it.sender ?: it.address)

            mapOperationLocalToOperation(it, accountName)
        }
    }

    override suspend fun syncOperationsFirstPage(pageSize: Int, account: WalletAccount, accounts: List<WalletAccount>): String? {
        val page = getOperations(pageSize, cursor = null, account, accounts)
        val accountAddress = account.address

        val elements = page.map { mapOperationToOperationLocalDb(it, OperationLocal.Source.SUBQUERY) }
        operationDao.insertFromSubquery(accountAddress, elements)

        return if (page.isNotEmpty()) page.last().nextPageCursor else null
    }

    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        currentAccount: WalletAccount,
        accounts: List<WalletAccount>
    ): List<Operation> {
        return withContext(Dispatchers.Default) {
            val accountsByAddress = accounts.associateBy { it.address }

            val response = subscanApi.getSubscanHistory(
                SubqueryHistoryElementByAddressRequest(
                    currentAccount.address,
                    pageSize,
                    cursor
                )
            ).data.query

            val pageInfo = response.historyElements.pageInfo

            val operations = response.historyElements.nodes.map {
                val accountName = defineAccountNameForTransaction(accountsByAddress, currentAccount.address, it.transfer?.from, it.transfer?.to)
                mapNodesToOperation(it, pageInfo.endCursor, currentAccount, accountName)
            }

            operations
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

    private fun defineAccountNameForTransaction(
        accountsByAddress: Map<String, WalletAccount>,
        transactionAccountAddress: String,
        recipientAddress: String?,
        senderAddress: String?
    ): String? {
        if (recipientAddress == null && senderAddress == null) return null
        val accountAddress = if (transactionAccountAddress == recipientAddress) {
            senderAddress
        } else {
            recipientAddress
        }
        return accountsByAddress[accountAddress]?.name
    }

    private fun defineAccountNameForTransaction(
        accountsByAddress: Map<String, WalletAccount>,
        displayAddress: String?
    ): String? {
        if (displayAddress == null) return null
        return accountsByAddress[displayAddress]?.name
    }

    private fun createOperation(hash: String, transfer: Transfer, senderAddress: String, fee: BigDecimal, source: OperationLocal.Source) =
        OperationLocal(
            hash = hash,
            address = senderAddress,
            time = System.currentTimeMillis(),
            tokenType = mapTokenTypeToTokenTypeLocal(transfer.tokenType),
            call = "Transfer",
            amount = transfer.amount.toBigInteger(),
            sender = senderAddress,
            receiver = transfer.recipient,
            fee = fee.toBigInteger(),
            status = OperationLocal.Status.PENDING,
            source = source
        )

    private suspend fun updateAssetRates(
        account: WalletAccount,
        todayResponse: SubscanResponse<AssetPriceStatistics>?,
        yesterdayResponse: SubscanResponse<AssetPriceStatistics>?
    ) = assetCache.updateToken(account.address.networkType()) { cached ->
        val todayStats = todayResponse?.content
        val yesterdayStats = yesterdayResponse?.content

        var mostRecentPrice = todayStats?.price

        if (mostRecentPrice == null) {
            mostRecentPrice = cached.dollarRate
        }

        val change = todayStats?.calculateRateChange(yesterdayStats)

        cached.copy(
            dollarRate = mostRecentPrice,
            recentRateChange = change
        )
    }

    private suspend fun getAssetPrice(networkType: Node.NetworkType, request: AssetPriceRequest): SubscanResponse<AssetPriceStatistics> {
        return try {
            apiCall { subscanApi.getAssetPrice(networkType.subscanSubDomain(), request) }
        } catch (_: Exception) {
            SubscanResponse.createEmptyResponse()
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)
}
