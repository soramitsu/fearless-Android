package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.Function3
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetLocalToAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionLocalToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransferToTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubscanResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.feeFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.miscFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.reserved
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanError
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
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

    override fun syncAssets(): Completable {
        return getSelectedAccount()
            .doOnSuccess(::syncAssets)
            .ignoreElement()
    }

    override fun observeAsset(token: Asset.Token): Observable<Asset> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAsset(account.address, token)
        }.map(::mapAssetLocalToAsset)
    }

    override fun syncAsset(token: Asset.Token): Completable {
        return syncAssets()
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

    override fun getContacts(query: String) : Single<List<String>> {
        return transactionsDao.getContacts(query)
    }

    private fun syncTransactionsFirstPage(pageSize: Int, account: Account): Completable {
        return getTransactionPage(pageSize, 0, account)
            .mapList { mapTransactionToTransactionLocal(it, account.address) }
            .doOnSuccess { transactionsDao.clearAndInsert(account.address, it) }
            .ignoreElement()
    }

    private fun syncAssets(account: Account) {
        val node = accountRepository.getSelectedNode().blockingGet()

        val assets = zipSyncAssetRequests(account, node).blockingGet()

        val assetsLocal = assets.map { mapAssetToAssetLocal(it, account.address) }

        assetDao.insert(assetsLocal)
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

    private fun zipSyncAssetRequests(
        account: Account,
        node: Node
    ): Single<List<Asset>> {
        val accountInfoSingle = substrateSource.fetchAccountInfo(account, node)

        val networkType = account.network.type

        val currentPriceStatsSingle = getAssetPrice(networkType, AssetPriceRequest.createForNow())
        val yesterdayPriceStatsSingle = getAssetPrice(networkType, AssetPriceRequest.createForYesterday())

        return Single.zip(accountInfoSingle,
            currentPriceStatsSingle,
            yesterdayPriceStatsSingle,
            Function3<EncodableStruct<AccountInfo>, SubscanResponse<AssetPriceStatistics>, SubscanResponse<AssetPriceStatistics>, List<Asset>> { accountInfo, nowStats, yesterdayStats ->
                listOf(
                    createAsset(account.network.type, accountInfo, nowStats, yesterdayStats)
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

    private fun subDomainFor(networkType: Node.NetworkType): String {
        return networkType.readableName.toLowerCase(Locale.ROOT)
    }
}
