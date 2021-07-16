package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.subscan.SubscanResponse
import jp.co.soramitsu.common.data.network.subscan.subscanSubDomain
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.dao.SubqueryHistoryDao
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.SubqueryHistoryModel
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.tokenTypeLocalFromNetworkType
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.SubqueryElement
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapNodesToSubqueryElements
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapSubqueryDbToSubqueryElement
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapSubqueryElementToSubqueryHistoryDb
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransferToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryHistoryElementByAddressRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
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
    private val transactionsDao: TransactionDao,
    private val subqueryDao: SubqueryHistoryDao,
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

    override fun transactionsFirstPageFlow(currentAccount: WalletAccount, pageSize: Int, accounts: List<WalletAccount>): Flow<List<Transaction>> {
        return observeTransactions(currentAccount, accounts)
    }

    override fun newTransactionsFirstPageFlow(currentAccount: WalletAccount, accounts: List<WalletAccount>): Flow<List<SubqueryElement>> {
        val accountsByAddress = accounts.associateBy { it.address }

        return subqueryDao.observe(currentAccount.address).mapList {
            val accountName = defineAccountNameForTransaction(accountsByAddress, displayAddress = it.receiver ?: it.sender ?: it.address)

            mapSubqueryDbToSubqueryElement(it, accountName)
        }
    }

    // Ready
    override suspend fun syncTransactionsFirstPage(pageSize: Int, account: WalletAccount, accounts: List<WalletAccount>): String? {
        val page = getNewTransactions(pageSize, cursor = null, account, accounts)
        val accountAddress = account.address
//
//        val toInsertLocally = page.map {
//            mapTransactionToTransactionLocal(it, accountAddress, TransactionLocal.Source.SUBSCAN)
//        }

//        transactionsDao.insertFromSubScan(accountAddress, toInsertLocally)
        val elements = page.map { mapSubqueryElementToSubqueryHistoryDb(it) }
        subqueryDao.insertFromSubquery(accountAddress, elements)

        return if (page.isNotEmpty()) page.last().nextPageCursor else null // TODO hz
    }

    override suspend fun getTransactionPage(pageSize: Int, page: Int, currentAccount: WalletAccount, accounts: List<WalletAccount>): List<Transaction> {
        return withContext(Dispatchers.Default) {
            val subDomain = currentAccount.network.type.subscanSubDomain()
            val request = TransactionHistoryRequest(currentAccount.address, pageSize, page)

            val response = apiCall { subscanApi.getTransactionHistory(subDomain, request) }

            val transfers = response.content?.transfers
            val accountsByAddress = accounts.associateBy { it.address }
            val transactions = transfers?.map {
                val accountName = defineAccountNameForTransaction(accountsByAddress, currentAccount.address, it.from, it.to)
                mapTransferToTransaction(it, currentAccount, accountName)
            }

            transactions ?: getCachedTransactions(page, currentAccount, accounts)
        }
    }

    override suspend fun getNewTransactions(
        pageSize: Int,
        cursor: String?,
        currentAccount: WalletAccount,
        accounts: List<WalletAccount>
    ): List<SubqueryElement> {
        val accountsByAddress = accounts.associateBy { it.address }

        val response = subscanApi.getSumReward(
            SubqueryHistoryElementByAddressRequest(
                currentAccount.address,
                pageSize,
                cursor
            )
        ).data.query

        val pageInfo = response.historyElements.pageInfo

        val result = response.historyElements.nodes.map {
            val accountName = defineAccountNameForTransaction(accountsByAddress, currentAccount.address, it.transfer?.from, it.transfer?.to)
            mapNodesToSubqueryElements(it, pageInfo.endCursor, currentAccount, accountName)
        }

        return result
    }

    override suspend fun getContacts(account: WalletAccount, query: String): Set<String> {
        return subqueryDao.getContacts(query, account.address).toSet()
    }

    override suspend fun getTransferFee(accountAddress: String, transfer: Transfer): Fee {
        val feeRemote = substrateSource.getTransferFee(accountAddress, transfer)

        return mapFeeRemoteToFee(feeRemote, transfer)
    }

    override suspend fun performTransfer(accountAddress: String, transfer: Transfer, fee: BigDecimal) {
        val transactionHash = substrateSource.performTransfer(accountAddress, transfer)

        val transaction = createSubqueryElement(transactionHash, transfer, accountAddress, fee)

        subqueryDao.insert(transaction)//TODO transaction source
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

    //FIXME выглядит оч криво
    private fun defineAccountNameForTransaction(
        accountsByAddress: Map<String, WalletAccount>,
        displayAddress: String?
    ): String? {
        if (displayAddress == null) return null
        return accountsByAddress[displayAddress]?.name
    }

    //TODO status
    private fun createSubqueryElement(hash: String, transfer: Transfer, senderAddress: String, fee: BigDecimal) =
        SubqueryHistoryModel(
            hash = hash,
            address = senderAddress,
            time = System.currentTimeMillis(),
            tokenType = mapTokenTypeToTokenTypeLocal(transfer.tokenType),
            call = "Transfer",
            amount = transfer.amount.toBigInteger(),
            sender = senderAddress,
            receiver = transfer.recipient,
            fee = fee.toBigInteger()
        )

    private suspend fun getCachedTransactions(page: Int, currentAccount: WalletAccount, accounts: List<WalletAccount>): List<Transaction> {
        return if (page == 0) {
            val accountsByAddress = accounts.associateBy { it.address }
            transactionsDao.getTransactions(currentAccount.address)
                .map {
                    val accountName = defineAccountNameForTransaction(accountsByAddress, it.accountAddress, it.recipientAddress, it.senderAddress)
                    mapTransactionLocalToTransaction(it, accountName)
                }
        } else {
            emptyList()
        }
    }

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

    private fun observeTransactions(currentAccount: WalletAccount, accounts: List<WalletAccount>): Flow<List<Transaction>> {
        return transactionsDao.observeTransactions(currentAccount.address)
            .map {
                val accountsByAddress = accounts.associateBy { it.address }
                it.map {
                    val accountName = defineAccountNameForTransaction(accountsByAddress, it.accountAddress, it.recipientAddress, it.senderAddress)
                    mapTransactionLocalToTransaction(it, accountName)
                }
            }
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
