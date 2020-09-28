package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.Function3
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.toAsset
import jp.co.soramitsu.feature_wallet_impl.data.mappers.toLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.toTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubscanResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
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

    override fun getAssets(): Observable<List<Asset>> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAssets(account.address)
        }.mapList(AssetLocal::toAsset)
    }

    override fun syncAssets(): Completable {
        return getSelectedAccount()
            .doOnSuccess(::syncAssets)
            .ignoreElement()
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

    private fun syncTransactionsFirstPage(pageSize: Int, account: Account): Completable {
        return getTransactionPage(pageSize, 0, account)
            .mapList { it.toLocal(account.address) }
            .doOnSuccess { transactionsDao.clearAndInsert(account.address, it) }
            .ignoreElement()
    }

    private fun syncAssets(account: Account) {
        val node = accountRepository.getSelectedNode().blockingGet()

        val assets = zipSyncAssetRequests(account, node).blockingGet()

        val assetsLocal = assets.map { it.toLocal(account.address) }

        assetDao.insert(assetsLocal)
    }

    private fun getTransactionPage(pageSize: Int, page: Int, account: Account): Single<List<Transaction>> {
        val subDomain = subDomainFor(account.network.type)
        val request = TransactionHistoryRequest(account.address, pageSize, page)

        return subscanApi.getTransactionHistory(subDomain, request)
            .map {
                val content = it.content ?: throw SubscanError(it.message)

                val transfers = content.transfers ?: emptyList()

                transfers.map { transfer -> transfer.toTransaction(account) }
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

        val balanceInPlanks = accountInfo[data][free]
        val mostRecentPrice = todayStats?.price

        val change = todayStats?.calculateRateChange(yesterdayStats)

        return Asset(
            Asset.Token.fromNetworkType(networkType),
            balanceInPlanks,
            mostRecentPrice,
            change
        )
    }

    private fun observeTransactions(accountAddress: String): Observable<List<Transaction>> {
        return transactionsDao.observeTransactions(accountAddress)
            .mapList(TransactionLocal::toTransaction)
    }

    private fun getSelectedAccount() = accountRepository.observeSelectedAccount().firstOrError()

    private fun fake(pageSize: Int, page: Int, account: Account) = Single.fromCallable {
        (0..pageSize).map {
            val timestamp = System.currentTimeMillis()
            val hash = ((pageSize + 1) * page + it).toString() + account.network.type.readableName

            val address1 = account.network.type.readableName + "5DEwU2U97RnBHCpfwHMDfJC7pqAdfWaPFib9wiZcr2ephSfT"
            val address2 = account.network.type.readableName + "F2dMuaCik4Ackmo9hoMMV79ETtVNvKSZMVK5sue9q1syPrW"

            Transaction(
                hash, Asset.Token.KSM,
                address1,
                address2,
                BigDecimal.TEN,
                timestamp,
                it % 2 == 0
            )
        }
    }

    private fun getAssetPrice(networkType: Node.NetworkType, request: AssetPriceRequest): Single<SubscanResponse<AssetPriceStatistics>> {
        return subscanApi.getAssetPrice(subDomainFor(networkType), request)
    }

    private fun subDomainFor(networkType: Node.NetworkType): String {
        return networkType.readableName.toLowerCase(Locale.ROOT)
    }
}
