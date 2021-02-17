package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.mappers.mapSigningDataToKeypair
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransferToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubscanResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Locale

@Suppress("EXPERIMENTAL_API_USAGE")
class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val accountRepository: AccountRepository,
    private val transactionsDao: TransactionDao,
    private val subscanApi: SubscanNetworkApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val assetCache: AssetCache,
    private val phishingAddressDao: PhishingAddressDao
) : WalletRepository {

    override fun assetsFlow(): Flow<List<Asset>> {
        return accountRepository.selectedAccountFlow()
            .flatMapLatest { account -> assetCache.observeAssets(account.address) }
            .mapList(::mapAssetLocalToAsset)
    }

    override suspend fun syncAssetsRates() {
        val account = accountRepository.getSelectedAccount()

        syncAssetRates(account)
    }

    override fun assetFlow(type: Token.Type): Flow<Asset> {
        return accountRepository.selectedAccountFlow()
            .flatMapLatest { account -> assetCache.observeAsset(account.address, type) }
            .map { mapAssetLocalToAsset(it) }
    }

    override suspend fun getAsset(type: Token.Type): Asset? {
        val account = accountRepository.getSelectedAccount()

        val assetLocal = assetCache.getAsset(account.address, type)

        return assetLocal?.let(::mapAssetLocalToAsset)
    }

    override suspend fun syncAsset(type: Token.Type) {
        return syncAssetsRates()
    }

    override fun transactionsFirstPageFlow(currentAccount: WalletAccount, pageSize: Int, accounts: List<WalletAccount>): Flow<List<Transaction>> {
        return observeTransactions(currentAccount, accounts)
    }

    override suspend fun syncTransactionsFirstPage(pageSize: Int, account: WalletAccount, accounts: List<WalletAccount>) {
        val page = getTransactionPage(pageSize, 0, account, accounts)
        val accountAddress = account.address

        val toInsertLocally = page.map {
            mapTransactionToTransactionLocal(it, accountAddress, TransactionSource.SUBSCAN)
        }

        transactionsDao.insertFromSubScan(accountAddress, toInsertLocally)
    }

    override suspend fun getTransactionPage(pageSize: Int, page: Int, currentAccount: WalletAccount, accounts: List<WalletAccount>): List<Transaction> {
        return withContext(Dispatchers.Default) {
            val subDomain = subDomainFor(currentAccount.network.type)
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

    override suspend fun getContacts(query: String): Set<String> {
        val account = accountRepository.getSelectedAccount()

        return transactionsDao.getContacts(query, account.address).toSet()
    }

    override suspend fun getTransferFee(transfer: Transfer): Fee {
        val account = accountRepository.getSelectedAccount()

        val feeRemote = substrateSource.getTransferFee(account, transfer)

        return mapFeeRemoteToFee(feeRemote, transfer)
    }

    override suspend fun performTransfer(transfer: Transfer, fee: BigDecimal) {
        val account = accountRepository.getSelectedAccount()
        val signingData = accountRepository.getCurrentSecuritySource().signingData
        val keypair = mapSigningDataToKeypair(signingData)

        val transactionHash = substrateSource.performTransfer(account, transfer, keypair)

        val transaction = createTransaction(transactionHash, transfer, account.address, fee)

        val transactionLocal = mapTransactionToTransactionLocal(transaction, account.address, TransactionSource.APP)

        transactionsDao.insert(transactionLocal)
    }

    override suspend fun checkTransferValidity(transfer: Transfer): TransferValidityStatus {
        val account = accountRepository.getSelectedAccount()
        val feeResponse = getTransferFee(transfer)

        val tokenType = transfer.tokenType

        val recipientInfo = substrateSource.fetchAccountInfo(transfer.recipient, account.network.type)
        val totalRecipientBalanceInPlanks = recipientInfo.totalBalanceInPlanks()
        val totalRecipientBalance = tokenType.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetCache.getAsset(account.address, transfer.tokenType)!!
        val asset = mapAssetLocalToAsset(assetLocal)

        return transfer.validityStatus(asset.transferable, asset.total, feeResponse.feeAmount, totalRecipientBalance)
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

    private fun defineAccountNameForTransaction(
        accountsByAddress: Map<String, WalletAccount>,
        transactionAccountAddress: String,
        recipientAddress: String,
        senderAddress: String
    ): String? {
        val accountAddress = if (transactionAccountAddress == recipientAddress) {
            senderAddress
        } else {
            recipientAddress
        }
        return accountsByAddress[accountAddress]?.name
    }

    private fun createTransaction(hash: String, transfer: Transfer, senderAddress: String, fee: BigDecimal) =
        Transaction(
            hash = hash,
            tokenType = transfer.tokenType,
            senderAddress = senderAddress,
            recipientAddress = transfer.recipient,
            amount = transfer.amount,
            date = System.currentTimeMillis(),
            isIncome = false,
            fee = fee,
            status = Transaction.Status.PENDING,
            accountName = null
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

    private suspend fun syncAssetRates(account: Account) = coroutineScope {
        val networkType = account.network.type

        val currentPriceStatsDeferred = async { getAssetPrice(networkType, AssetPriceRequest.createForNow()) }
        val yesterdayPriceStatsDeferred = async { getAssetPrice(networkType, AssetPriceRequest.createForYesterday()) }

        updateAssetRates(account, currentPriceStatsDeferred.await(), yesterdayPriceStatsDeferred.await())
    }

    private suspend fun updateAssetRates(
        account: Account,
        todayResponse: SubscanResponse<AssetPriceStatistics>?,
        yesterdayResponse: SubscanResponse<AssetPriceStatistics>?
    ) = assetCache.updateToken(account) { cached ->
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
            apiCall { subscanApi.getAssetPrice(subDomainFor(networkType), request) }
        } catch (_: Exception) {
            SubscanResponse.createEmptyResponse()
        }
    }

    private fun subDomainFor(networkType: Node.NetworkType): String {
        return networkType.readableName.toLowerCase(Locale.ROOT)
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)
}