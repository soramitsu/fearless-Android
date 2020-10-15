package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.Function3
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SigningData
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapFeeRemoteToFee
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransferToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.FeeRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubscanResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.feeFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.miscFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.reserved
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanError
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import java.math.BigDecimal
import java.util.Locale

class WalletRepositoryImpl(
    private val substrateSource: WssSubstrateSource,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao,
    private val transactionsDao: TransactionDao,
    private val subscanApi: SubscanNetworkApi
) : WalletRepository {

    override fun observeAssets(): Observable<List<Asset>> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAssets(account.address)
        }.mapList(::mapAssetLocalToAsset)
    }

    override fun syncAssets(withoutRates: Boolean): Completable {
        return getSelectedAccount()
            .flatMapCompletable { syncAssets(it, withoutRates) }
    }

    override fun observeAsset(token: Asset.Token): Observable<Asset> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAsset(account.address, token)
        }.map(::mapAssetLocalToAsset)
    }

    override fun syncAsset(token: Asset.Token, withoutRates: Boolean): Completable {
        return syncAssets(withoutRates)
    }

    override fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>> {
        return accountRepository.observeSelectedAccount()
            .switchMap { observeTransactions(it.address) }
    }

    override fun syncTransactionsFirstPage(pageSize: Int): Completable {
        return getSelectedAccount()
            .flatMapCompletable { syncTransactionsFirstPage(pageSize, it) }
    }

    override fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>> {
        return getSelectedAccount()
            .flatMap { getTransactionPage(pageSize, page, it) }
    }

    override fun getContacts(query: String, networkType: Node.NetworkType): Single<List<String>> {
        return transactionsDao.getContacts(query, networkType)
    }

    override fun getTransferFee(transfer: Transfer): Single<Fee> {
        return getSelectedAccount()
            .flatMap { getTransferFeeUpdatingBalance(it, transfer) }
            .map { mapFeeRemoteToFee(it, transfer.token) }
    }

    override fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable {
        return getSelectedAccount().flatMap { account ->
            accountRepository.getSigningData()
                .map(this::mapSigningDataToKeypair)
                .flatMap { keys -> substrateSource.performTransfer(account, transfer, keys) }
                .map { hash -> createTransaction(hash, transfer, account.address, fee) }
                .map { transaction -> mapTransactionToTransactionLocal(transaction, account.address, TransactionSource.APP) }
        }.flatMapCompletable { transactionsDao.insert(it) }
    }

    override fun checkEnoughAmountForTransfer(transfer: Transfer): Single<Boolean> {
        return getSelectedAccount().flatMap { account ->
            substrateSource.fetchAccountInfo(account).flatMap { accountInfo ->
                getTransferFeeUpdatingBalance(account, transfer).map { fee ->
                    checkEnoughAmountForTransfer(transfer, accountInfo, fee)
                }
            }
        }
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
            .doOnSuccess { updateLocalBalance(account, it.newAccountInfo) }
            .map { it.feeRemote }
    }

    private fun checkEnoughAmountForTransfer(
        transfer: Transfer,
        accountInfo: EncodableStruct<AccountInfo>,
        fee: FeeRemote
    ): Boolean {
        val balance = accountInfo[data][free]

        return fee.partialFee + transfer.amountInPlanks <= balance
    }

    private fun syncTransactionsFirstPage(pageSize: Int, account: Account): Completable {
        return getTransactionPage(pageSize, 0, account)
            .mapList { mapTransactionToTransactionLocal(it, account.address, TransactionSource.SUBSCAN) }
            .doOnSuccess { transactionsDao.insertFromSubscan(account.address, it) }
            .ignoreElement()
    }

    private fun syncAssets(account: Account, withoutRates: Boolean): Completable {
        return if (withoutRates) {
            balanceAssetSync(account)
        } else {
            fullAssetSync(account)
        }
    }

    private fun getTransactionPage(pageSize: Int, page: Int, account: Account): Single<List<Transaction>> {
        val subDomain = subDomainFor(account.network.type)
        val request = TransactionHistoryRequest(account.address, pageSize, page)

        return subscanApi.getTransactionHistory(subDomain, request)
            .map {
                val content = it.content ?: throw SubscanError(it.message)

                val transfers = content.transfers ?: emptyList()

                transfers.map { transfer -> mapTransferToTransaction(transfer, account) }
            }
    }

    private fun fullAssetSync(account: Account): Completable {
        return zipSyncAssetRequests(account)
            .mapList { mapAssetToAssetLocal(it, account.address) }
            .flatMapCompletable(assetDao::insert)
    }

    private fun balanceAssetSync(account: Account): Completable {
        return substrateSource.fetchAccountInfo(account).flatMapCompletable { accountInfo ->
            updateLocalBalance(account, accountInfo)
        }
    }

    private fun updateLocalBalance(account: Account, accountInfo: EncodableStruct<AccountInfo>): Completable {
        return assetDao.observeAssets(account.address).firstOrError()
            .flatMapCompletable { currentState ->
                val asset = currentState.first()
                val accountData = accountInfo[data]

                val newState = listOf(
                    asset.copy(
                        feeFrozenInPlanks = accountData[feeFrozen],
                        miscFrozenInPlanks = accountData[miscFrozen],
                        freeInPlanks = accountData[free],
                        reservedInPlanks = accountData[reserved]
                    )
                )

                assetDao.insert(newState)
            }
    }

    private fun zipSyncAssetRequests(account: Account): Single<List<Asset>> {
        val accountInfoSingle = substrateSource.fetchAccountInfo(account)

        val networkType = account.network.type

        val currentPriceStatsSingle = getAssetPrice(networkType, AssetPriceRequest.createForNow())
        val yesterdayPriceStatsSingle = getAssetPrice(networkType, AssetPriceRequest.createForYesterday())

        return Single.zip(accountInfoSingle,
            currentPriceStatsSingle,
            yesterdayPriceStatsSingle,
            Function3<EncodableStruct<AccountInfo>, SubscanResponse<AssetPriceStatistics>, SubscanResponse<AssetPriceStatistics>, List<Asset>> { accountInfo, nowStats, yesterdayStats ->
                listOf(
                    createAsset(networkType, accountInfo, nowStats, yesterdayStats)
                )
            })
    }

    private fun createAsset(
        networkType: Node.NetworkType,
        accountInfo: EncodableStruct<AccountInfo>,
        todayResponse: SubscanResponse<AssetPriceStatistics>,
        yesterdayResponse: SubscanResponse<AssetPriceStatistics>
    ): Asset {
        val todayStats = todayResponse.content
        val yesterdayStats = yesterdayResponse.content

        val data = accountInfo[data]
        val mostRecentPrice = todayStats?.price

        val change = todayStats?.calculateRateChange(yesterdayStats)

        return Asset(
            Asset.Token.fromNetworkType(networkType),
            data[free],
            data[reserved],
            data[miscFrozen],
            data[feeFrozen],
            mostRecentPrice,
            change
        )
    }

    private fun observeTransactions(accountAddress: String): Observable<List<Transaction>> {
        return transactionsDao.observeTransactions(accountAddress)
            .mapList(::mapTransactionLocalToTransaction)
    }

    private fun getSelectedAccount() = accountRepository.observeSelectedAccount().firstOrError()

    private fun getAssetPrice(networkType: Node.NetworkType, request: AssetPriceRequest): Single<SubscanResponse<AssetPriceStatistics>> {
        return subscanApi.getAssetPrice(subDomainFor(networkType), request)
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
