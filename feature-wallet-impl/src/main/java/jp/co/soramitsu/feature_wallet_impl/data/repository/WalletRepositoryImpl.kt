package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.sumBy
import jp.co.soramitsu.common.utils.zip
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import jp.co.soramitsu.feature_account_api.domain.model.SigningData
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Error
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Ok
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Warning
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.calculateTotalBalance
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransferToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.feeFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.miscFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.reserved
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ActiveEraInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.UnlockChunk
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.hash
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubscanResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.TransactionHistory
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

@Suppress("EXPERIMENTAL_API_USAGE")
class WalletRepositoryImpl(
    private val substrateSource: WssSubstrateSource,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao,
    private val transactionsDao: TransactionDao,
    private val subscanApi: SubscanNetworkApi,
    private val sS58Encoder: SS58Encoder
) : WalletRepository {

    override fun observeAssets(): Observable<List<Asset>> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAssets(account.address)
        }.mapList(::mapAssetLocalToAsset)
    }

    override fun syncAssetsRates(): Completable {
        return accountRepository.getSelectedAccount()
            .flatMapCompletable(this::syncAssetRates)
    }

    override fun observeAsset(type: Token.Type): Observable<Asset> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAsset(account.address, type)
        }.map(::mapAssetLocalToAsset)
    }

    override fun syncAsset(type: Token.Type): Completable {
        return syncAssetsRates()
    }

    override fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>> {
        return accountRepository.observeSelectedAccount()
            .switchMap { observeTransactions(it.address) }
    }

    override fun syncTransactionsFirstPage(pageSize: Int): Completable {
        return accountRepository.getSelectedAccount()
            .flatMapCompletable { syncTransactionsFirstPage(pageSize, it) }
    }

    override fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>> {
        return accountRepository.getSelectedAccount()
            .flatMap { getTransactionPage(pageSize, page, it) }
    }

    override fun getContacts(query: String): Single<Set<String>> {
        return accountRepository.getSelectedAccount().flatMap { account ->
            transactionsDao.getContacts(query, account.address).map { it.toSet() }
        }
    }

    override fun getTransferFee(transfer: Transfer): Single<Fee> {
        return accountRepository.getSelectedAccount()
            .flatMap { getTransferFee(it, transfer) }
            .map { mapFeeRemoteToFee(it, transfer) }
    }

    override fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable {
        return accountRepository.getSelectedAccount().flatMap { account ->
            accountRepository.getCurrentSecuritySource()
                .map(SecuritySource::signingData)
                .map(::mapSigningDataToKeypair)
                .flatMap { keys -> substrateSource.performTransfer(account, transfer, keys) }
                .map { hash -> createTransaction(hash, transfer, account.address, fee) }
                .map { transaction -> mapTransactionToTransactionLocal(transaction, account.address, TransactionSource.APP) }
        }.flatMapCompletable { transactionsDao.insert(it) }
    }

    override fun checkTransferValidity(transfer: Transfer): Single<TransferValidityStatus> {
        return accountRepository.getSelectedAccount().flatMap { account ->
            getTransferFee(account, transfer).flatMap { fee ->
                substrateSource.fetchAccountInfo(transfer.recipient, account.network.type).map { recipientInfo ->
                    val assetLocal = assetDao.getAsset(account.address, transfer.type)!!

                    val recipientData = recipientInfo[data]
                    val totalRecipientBalance = calculateTotalBalance(recipientData[free], recipientData[reserved])

                    checkTransferValidity(
                        transfer,
                        mapAssetLocalToAsset(assetLocal),
                        fee,
                        totalRecipientBalance
                    )
                }
            }
        }
    }

    override fun listenForUpdates(account: Account): Completable {
        val balanceUpdates = substrateSource.listenForAccountUpdates(account)
            .flatMapCompletable { change ->
                updateAssetBalance(account, change.newAccountInfo)
                    .andThen(fetchTransactions(account, change.block))
            }

        val stakingUpdates = substrateSource.listenStakingLedger(account)
            .flatMapCompletable { stakingLedger ->
                substrateSource.getActiveEra().flatMapCompletable { era ->
                    updateAssetStaking(account, stakingLedger, era)
                }
            }

        return Completable.merge(listOf(balanceUpdates, stakingUpdates))
    }

    private fun updateAssetStaking(
        account: Account,
        stakingLedger: EncodableStruct<StakingLedger>,
        era: EncodableStruct<ActiveEraInfo>
    ): Completable {
        return updateLocalAssetCopy(account) { cached ->
            val eraIndex = era[ActiveEraInfo.index].toLong()

            val redeemable = sumStaking(stakingLedger) { it <= eraIndex }
            val unbonding = sumStaking(stakingLedger) { it > eraIndex }

            cached.copy(
                redeemableInPlanks = redeemable,
                unbondingInPlanks = unbonding,
                bondedInPlanks = stakingLedger[StakingLedger.active]
            )
        }
    }

    private fun sumStaking(
        stakingLedger: EncodableStruct<StakingLedger>,
        condition: (chunkEra: Long) -> Boolean
    ): BigInteger {
        return stakingLedger[StakingLedger.unlocking]
            .filter { condition(it[UnlockChunk.era].toLong()) }
            .sumBy { it[UnlockChunk.value] }
    }

    private fun fetchTransactions(account: Account, blockHash: String): Completable {
        return substrateSource.fetchAccountTransactionInBlock(blockHash, account)
            .mapList { submittableExtrinsic ->
                createTransactionLocal(submittableExtrinsic, account)
            }.flatMapCompletable(transactionsDao::insert)
    }

    private fun createTransaction(hash: String, transfer: Transfer, accountAddress: String, fee: BigDecimal) =
        Transaction(
            hash,
            transfer.type,
            accountAddress,
            transfer.recipient,
            transfer.amount,
            System.currentTimeMillis(),
            isIncome = false,
            fee = fee,
            status = Transaction.Status.PENDING
        )

    private fun getTransferFee(account: Account, transfer: Transfer): Single<FeeResponse> {
        return substrateSource.getTransferFee(account, transfer)
    }

    private fun checkTransferValidity(
        transfer: Transfer,
        asset: Asset,
        fee: FeeResponse,
        recipientBalanceInPlanks: BigInteger
    ): TransferValidityStatus {
        val transactionTotalInPlanks = fee.partialFee + transfer.amountInPlanks
        val transactionTotal = transfer.type.amountFromPlanks(transactionTotalInPlanks)

        val existentialDeposit = transfer.type.networkType.runtimeConfiguration.existentialDeposit

        val recipientBalance = transfer.type.amountFromPlanks(recipientBalanceInPlanks)

        return when {
            transactionTotal > asset.transferable -> Error.Status.NotEnoughFunds
            recipientBalance + transfer.amount < existentialDeposit -> Error.Status.DeadRecipient
            asset.total - transactionTotal < existentialDeposit -> Warning.Status.WillRemoveAccount
            else -> Ok
        }
    }

    private fun syncTransactionsFirstPage(pageSize: Int, account: Account): Completable {
        return getTransactionPage(pageSize, 0, account)
            .mapList { mapTransactionToTransactionLocal(it, account.address, TransactionSource.SUBSCAN) }
            .doOnSuccess { transactionsDao.insertFromSubscan(account.address, it) }
            .ignoreElement()
    }

    private fun getTransactionPage(pageSize: Int, page: Int, account: Account): Single<List<Transaction>> {
        val subDomain = subDomainFor(account.network.type)
        val request = TransactionHistoryRequest(account.address, pageSize, page)

        return getTransactionHistory(subDomain, request)
            .map {
                val transfers = it.content?.transfers

                val transactions = transfers?.map { transfer -> mapTransferToTransaction(transfer, account) }

                transactions ?: getCachedTransactions(page, account)
            }
    }

    private fun getTransactionHistory(
        subDomain: String,
        request: TransactionHistoryRequest
    ): Single<SubscanResponse<TransactionHistory>> {
        return subscanApi.getTransactionHistory(subDomain, request)
    }

    private fun getCachedTransactions(page: Int, account: Account): List<Transaction>? {
        return if (page == 0) {
            transactionsDao.getTransactions(account.address).map(::mapTransactionLocalToTransaction)
        } else {
            emptyList()
        }
    }

    private fun syncAssetRates(account: Account): Completable {
        val networkType = account.network.type

        val currentPriceStatsSingle = getAssetPrice(networkType, AssetPriceRequest.createForNow())
        val yesterdayPriceStatsSingle = getAssetPrice(networkType, AssetPriceRequest.createForYesterday())

        val requests = listOf(currentPriceStatsSingle, yesterdayPriceStatsSingle)

        return requests.zip()
            .flatMapCompletable { (
                nowStats: SubscanResponse<AssetPriceStatistics>,
                yesterdayStats: SubscanResponse<AssetPriceStatistics>) ->

                updateAssetRates(account, nowStats, yesterdayStats)
            }
    }

    private fun updateAssetRates(
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

    private fun updateAssetBalance(
        account: Account,
        accountInfo: EncodableStruct<AccountInfo>
    ) = updateLocalAssetCopy(account) { cachedAsset ->
        val data = accountInfo[data]

        cachedAsset.copy(
            freeInPlanks = data[free],
            reservedInPlanks = data[reserved],
            miscFrozenInPlanks = data[miscFrozen],
            feeFrozenInPlanks = data[feeFrozen]
        )
    }

    private fun updateLocalAssetCopy(
        account: Account,
        builder: (local: AssetLocal) -> AssetLocal
    ) = Completable.fromAction {
        synchronized(this) {
            val tokenType = Token.Type.fromNetworkType(account.network.type)

            if (!assetDao.isTokenExists(tokenType)) {
                assetDao.insertToken(TokenLocal.createEmpty(tokenType))
            }

            val cachedAsset = assetDao.getAsset(account.address, tokenType)?.asset ?: AssetLocal.createEmpty(tokenType, account.address)

            val newAsset = builder.invoke(cachedAsset)

            assetDao.insertBlocking(newAsset)
        }
    }

    private fun updateLocalTokenCopy(
        account: Account,
        builder: (local: TokenLocal) -> TokenLocal
    ) = Completable.fromAction {
        synchronized(this) {
            val tokenType = Token.Type.fromNetworkType(account.network.type)

            val tokenLocal = assetDao.getToken(tokenType) ?: TokenLocal.createEmpty(tokenType)

            val newAsset = builder.invoke(tokenLocal)

            assetDao.insertToken(newAsset)
        }
    }

    private fun createTransactionLocal(
        extrinsic: EncodableStruct<SubmittableExtrinsic>,
        account: Account
    ): TransactionLocal {
        val hash = extrinsic.hash()

        val localCopy = transactionsDao.getTransaction(hash)

        val fee = localCopy?.feeInPlanks

        val networkType = account.network.type
        val token = Token.Type.fromNetworkType(networkType)
        val addressType = AddressType.valueOf(networkType.toString())

        val signed = extrinsic[SubmittableExtrinsic.signedExtrinsic]
        val transferArgs = signed[SignedExtrinsic.call][Call.args]

        val senderAddress = sS58Encoder.encode(signed[SignedExtrinsic.accountId], addressType)
        val recipientAddress = sS58Encoder.encode(transferArgs[TransferArgs.recipientId], addressType)

        val amountInPlanks = transferArgs[TransferArgs.amount]

        return TransactionLocal(
            hash = hash,
            accountAddress = account.address,
            senderAddress = senderAddress,
            recipientAddress = recipientAddress,
            source = TransactionSource.BLOCKCHAIN,
            status = Transaction.Status.COMPLETED,
            feeInPlanks = fee,
            token = token,
            amount = token.amountFromPlanks(amountInPlanks),
            date = System.currentTimeMillis(),
            networkType = networkType
        )
    }

    private fun observeTransactions(accountAddress: String): Observable<List<Transaction>> {
        return transactionsDao.observeTransactions(accountAddress)
            .mapList(::mapTransactionLocalToTransaction)
    }

    private fun getAssetPrice(networkType: Node.NetworkType, request: AssetPriceRequest): Single<SubscanResponse<AssetPriceStatistics>> {
        return subscanApi.getAssetPrice(subDomainFor(networkType), request)
            .onErrorReturnItem(SubscanResponse.createEmptyResponse())
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
}
