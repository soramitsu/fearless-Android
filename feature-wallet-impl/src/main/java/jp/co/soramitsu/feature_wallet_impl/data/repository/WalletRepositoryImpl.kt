package jp.co.soramitsu.feature_wallet_impl.data.repository

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.utils.encode
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SigningData
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransferToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ActiveEraInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsic
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Locale

@Suppress("EXPERIMENTAL_API_USAGE")
class WalletRepositoryImpl(
    private val substrateSource: SubstrateRemoteSource,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao,
    private val transactionsDao: TransactionDao,
    private val subscanApi: SubscanNetworkApi,
    private val sS58Encoder: SS58Encoder,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val phishingApi: PhishingApi,
    private val phishingAddressDao: PhishingAddressDao
) : WalletRepository {

    override fun assetsFlow(): Flow<List<Asset>> {
        return accountRepository.selectedAccountFlow()
            .flatMapLatest { account -> assetDao.observeAssets(account.address) }
            .mapList(::mapAssetLocalToAsset)
    }

    override suspend fun syncAssetsRates() {
        val account = accountRepository.getSelectedAccount()

        syncAssetRates(account)
    }

    override fun assetFlow(type: Token.Type): Flow<Asset> {
        return accountRepository.selectedAccountFlow()
            .flatMapLatest { account -> assetDao.observeAsset(account.address, type) }
            .map { mapAssetLocalToAsset(it) }
    }

    override suspend fun getAsset(type: Token.Type): Asset? {
        val account = accountRepository.getSelectedAccount()

        val assetLocal = assetDao.getAsset(account.address, type)

        return assetLocal?.let(::mapAssetLocalToAsset)
    }

    override suspend fun syncAsset(type: Token.Type) {
        return syncAssetsRates()
    }

    override fun transactionsFirstPageFlow(pageSize: Int): Flow<List<Transaction>> {
        return accountRepository.selectedAccountFlow()
            .flatMapLatest { observeTransactions(it.address) }
    }

    override suspend fun syncTransactionsFirstPage(pageSize: Int) {
        val account = accountRepository.getSelectedAccount()

        syncTransactionsFirstPage(pageSize, account)
    }

    override suspend fun getTransactionPage(pageSize: Int, page: Int): List<Transaction> {
        val account = accountRepository.getSelectedAccount()

        return getTransactionPage(pageSize, page, account)
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

        val transaction = createTransaction(transactionHash, transfer, senderAddress = account.address, fee)

        val transactionLocal = mapTransactionToTransactionLocal(transaction, accountAddress = account.address, TransactionSource.APP)

        transactionsDao.insert(transactionLocal)
    }

    override suspend fun checkTransferValidity(transfer: Transfer): TransferValidityStatus {
        val account = accountRepository.getSelectedAccount()
        val feeResponse = getTransferFee(transfer)

        val tokenType = transfer.tokenType

        val recipientInfo = substrateSource.fetchAccountInfo(transfer.recipient, account.network.type)
        val totalRecipientBalanceInPlanks = recipientInfo.totalBalanceInPlanks()
        val totalRecipientBalance = tokenType.amountFromPlanks(totalRecipientBalanceInPlanks)

        val assetLocal = assetDao.getAsset(account.address, transfer.tokenType)!!
        val asset = mapAssetLocalToAsset(assetLocal)

        return transfer.validityStatus(asset.transferable, asset.total, feeResponse.feeAmount, totalRecipientBalance)
    }

    override suspend fun listenForAccountInfoUpdates(account: Account) {
        substrateSource.listenForAccountUpdates(account.address)
            .onEach { change ->
                updateAssetBalance(account, change.newAccountInfo)

                fetchTransfers(account, change.block)
            }.collect()
    }

    override suspend fun listenForStakingLedgerUpdates(account: Account) {
        substrateSource.listenStakingLedger(account.address)
            .onEach { stakingLedger ->
                val era = substrateSource.getActiveEra()

                updateAssetStaking(account, stakingLedger, era)
            }.collect()
    }

    override suspend fun updatePhishingAddresses() = withContext(Dispatchers.Default) {
        val publicKeys = phishingApi.getPhishingAddresses().entries.map { it.value }.flatten()
            .map { sS58Encoder.decode(it).toHexString(withPrefix = true) }

        val phishingAddressesLocal = publicKeys.map { PhishingAddressLocal(it) }

        phishingAddressDao.clearTable()
        phishingAddressDao.insert(phishingAddressesLocal)
    }

    override suspend fun isAddressFromPhishingList(address: String) = withContext(Dispatchers.Default) {
        val phishingAddresses = phishingAddressDao.getAll()

        val addressPublicKey = sS58Encoder.decode(address).toHexString(withPrefix = true)

        phishingAddresses.map { it.publicKey }.contains(addressPublicKey)
    }

    private suspend fun updateAssetStaking(
        account: Account,
        stakingLedger: EncodableStruct<StakingLedger>,
        era: EncodableStruct<ActiveEraInfo>
    ) {
        return updateLocalAssetCopy(account) { cached ->
            val eraIndex = era[ActiveEraInfo.index].toLong()

            val redeemable = stakingLedger.sumStaking { it <= eraIndex }
            val unbonding = stakingLedger.sumStaking { it > eraIndex }

            cached.copy(
                redeemableInPlanks = redeemable,
                unbondingInPlanks = unbonding,
                bondedInPlanks = stakingLedger[StakingLedger.active]
            )
        }
    }

    private suspend fun fetchTransfers(account: Account, blockHash: String) {
        val transactions = substrateSource.fetchAccountTransfersInBlock(blockHash, account)
        val local = transactions.map { createTransactionLocal(it, account) }

        transactionsDao.insert(local)
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
            status = Transaction.Status.PENDING
        )

    private suspend fun syncTransactionsFirstPage(pageSize: Int, account: Account) {
        val page = getTransactionPage(pageSize, 0, account)
        val accountAddress = account.address

        val toInsertLocally = page.map {
            mapTransactionToTransactionLocal(it, accountAddress, TransactionSource.SUBSCAN)
        }

        transactionsDao.insertFromSubScan(accountAddress, toInsertLocally)
    }

    private suspend fun getTransactionPage(pageSize: Int, page: Int, account: Account): List<Transaction> {
        val subDomain = subDomainFor(account.network.type)
        val request = TransactionHistoryRequest(account.address, pageSize, page)

        val response = apiCall { subscanApi.getTransactionHistory(subDomain, request) }

        val transfers = response.content?.transfers
        val transactions = transfers?.map { transfer -> mapTransferToTransaction(transfer, account) }

        return transactions ?: getCachedTransactions(page, account)
    }

    private suspend fun getCachedTransactions(page: Int, account: Account): List<Transaction> {
        return if (page == 0) {
            transactionsDao.getTransactions(account.address).map(::mapTransactionLocalToTransaction)
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
    ) = updateLocalTokenCopy(account) { cached ->
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

    private suspend fun updateAssetBalance(
        account: Account,
        accountInfo: EncodableStruct<AccountInfoSchema>
    ) = updateLocalAssetCopy(account) { cachedAsset ->
        val data = accountInfo[accountInfo.schema.data]

        cachedAsset.copy(
            freeInPlanks = data[AccountData.free],
            reservedInPlanks = data[AccountData.reserved],
            miscFrozenInPlanks = data[AccountData.miscFrozen],
            feeFrozenInPlanks = data[AccountData.feeFrozen]
        )
    }

    private val assetUpdateMutex = Mutex()

    private suspend fun updateLocalAssetCopy(
        account: Account,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenType = Token.Type.fromNetworkType(account.network.type)

            if (!assetDao.isTokenExists(tokenType)) {
                assetDao.insertToken(TokenLocal.createEmpty(tokenType))
            }

            val cachedAsset = assetDao.getAsset(account.address, tokenType)?.asset ?: AssetLocal.createEmpty(tokenType, account.address)

            val newAsset = builder.invoke(cachedAsset)

            assetDao.insertAsset(newAsset)
        }
    }

    private suspend fun updateLocalTokenCopy(
        account: Account,
        builder: (local: TokenLocal) -> TokenLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenType = Token.Type.fromNetworkType(account.network.type)

            val tokenLocal = assetDao.getToken(tokenType) ?: TokenLocal.createEmpty(tokenType)

            val newAsset = builder.invoke(tokenLocal)

            assetDao.insertToken(newAsset)
        }
    }

    private suspend fun createTransactionLocal(
        extrinsic: TransferExtrinsic,
        account: Account
    ): TransactionLocal {
        val localCopy = transactionsDao.getTransaction(extrinsic.hash)

        val fee = localCopy?.feeInPlanks

        val networkType = account.network.type
        val token = Token.Type.fromNetworkType(networkType)

        val senderAddress = sS58Encoder.encode(extrinsic.senderId, networkType)
        val recipientAddress = sS58Encoder.encode(extrinsic.recipientId, networkType)

        return TransactionLocal(
            hash = extrinsic.hash,
            accountAddress = account.address,
            senderAddress = senderAddress,
            recipientAddress = recipientAddress,
            source = TransactionSource.BLOCKCHAIN,
            status = Transaction.Status.COMPLETED,
            feeInPlanks = fee,
            token = token,
            amount = token.amountFromPlanks(extrinsic.amountInPlanks),
            date = System.currentTimeMillis(),
            networkType = networkType
        )
    }

    private fun observeTransactions(accountAddress: String): Flow<List<Transaction>> {
        return transactionsDao.observeTransactions(accountAddress)
            .mapList(::mapTransactionLocalToTransaction)
    }

    private suspend fun getAssetPrice(networkType: Node.NetworkType, request: AssetPriceRequest): SubscanResponse<AssetPriceStatistics> {
        return try {
            apiCall { subscanApi.getAssetPrice(subDomainFor(networkType), request) }
        } catch (_: Exception) {
            SubscanResponse.createEmptyResponse()
        }
    }

    private fun mapSigningDataToKeypair(singingData: SigningData): Keypair {
        return with(singingData) {
            Keypair(
                publicKey = publicKey,
                privateKey = privateKey,
                nonce = nonce
            )
        }
    }

    private fun subDomainFor(networkType: Node.NetworkType): String {
        return networkType.readableName.toLowerCase(Locale.ROOT)
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = httpExceptionHandler.wrap(block)
}
