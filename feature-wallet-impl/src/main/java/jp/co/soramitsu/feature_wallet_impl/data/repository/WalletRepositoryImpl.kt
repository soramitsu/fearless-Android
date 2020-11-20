package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.sumBy
import jp.co.soramitsu.common.utils.zip
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.AssetLocal
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
import jp.co.soramitsu.feature_wallet_api.domain.model.CheckFundsStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransferToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeRemote
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

    override fun observeAsset(token: Asset.Token): Observable<Asset> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAsset(account.address, token)
        }.map(::mapAssetLocalToAsset)
    }

    override fun syncAsset(token: Asset.Token): Completable {
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

    override fun getContacts(query: String): Single<List<String>> {
        return accountRepository.getSelectedAccount().flatMap {
            transactionsDao.getContacts(query, it.address)
        }
    }

    override fun getTransferFee(transfer: Transfer): Single<Fee> {
        return accountRepository.getSelectedAccount()
            .flatMap { getTransferFeeUpdatingBalance(it, transfer) }
            .map { mapFeeRemoteToFee(it, transfer.token) }
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

    override fun checkEnoughAmountForTransfer(transfer: Transfer): Single<CheckFundsStatus> {
        return accountRepository.getSelectedAccount().flatMap { account ->
            getTransferFeeUpdatingBalance(account, transfer).map { fee ->
                val assetLocal = assetDao.getAsset(account.address, transfer.token)!!

                checkEnoughAmountForTransfer(transfer, mapAssetLocalToAsset(assetLocal), fee)
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
            transfer.token,
            accountAddress,
            transfer.recipient,
            transfer.amount,
            System.currentTimeMillis(),
            isIncome = false,
            fee = Fee(fee, transfer.token),
            status = Transaction.Status.PENDING
        )

    private fun getTransferFeeUpdatingBalance(account: Account, transfer: Transfer): Single<FeeRemote> {
        return substrateSource.getTransferFee(account, transfer)
            .doOnSuccess { updateAssetBalance(account, it.newAccountInfo) }
            .map { it.feeRemote }
    }

    private fun checkEnoughAmountForTransfer(
        transfer: Transfer,
        asset: Asset,
        fee: FeeRemote
    ): CheckFundsStatus {
        val transactionTotalInPlanks = fee.partialFee + transfer.amountInPlanks
        val transactionTotal = transfer.token.amountFromPlanks(transactionTotalInPlanks)

        val existentialDeposit = transfer.token.networkType.runtimeConfiguration.existentialDeposit

        return when {
            transactionTotal > asset.transferable -> CheckFundsStatus.NOT_ENOUGH_FUNDS
            asset.total - transactionTotal < existentialDeposit -> CheckFundsStatus.WILL_DESTROY_ACCOUNT
            else -> CheckFundsStatus.OK
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
    ) = updateLocalAssetCopy(account) { cached ->
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
    ) = updateLocalAssetCopy(account) { cached ->
        val data = accountInfo[data]

        cached.copy(
            freeInPlanks = data[free],
            reservedInPlanks = data[reserved],
            miscFrozenInPlanks = data[miscFrozen],
            feeFrozenInPlanks = data[feeFrozen]
        )
    }

    private fun updateLocalAssetCopy(
        account: Account,
        builder: (localSource: AssetLocal) -> AssetLocal
    ) = Completable.fromAction {
        synchronized(this) {
            val token = Asset.Token.fromNetworkType(account.network.type)
            val cachedAsset = assetDao.getAsset(account.address, token) ?: AssetLocal.createEmpty(token, account.address)

            val newAsset = builder.invoke(cachedAsset)

            assetDao.insertBlocking(newAsset)
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
        val token = Asset.Token.fromNetworkType(networkType)
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
