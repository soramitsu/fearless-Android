package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.Function3
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.toAsset
import jp.co.soramitsu.feature_wallet_impl.data.toLocal
import java.util.Locale

class WalletRepositoryImpl(
    private val substrateSource: WssSubstrateSource,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao,
    private val subscanApi: SubscanNetworkApi
) : WalletRepository {

    override fun getAssets(): Observable<List<Asset>> {
        return accountRepository.observeSelectedAccount().switchMap { account ->
            assetDao.observeAssets(account.address)
        }.map { it.map(AssetLocal::toAsset) }
    }

    override fun syncAssets(): Completable {
        return accountRepository.observeSelectedAccount().firstOrError()
            .doOnSuccess(::syncAssets)
            .ignoreElement()
    }

    private fun syncAssets(account: Account) {
        val node = accountRepository.getSelectedNode().blockingGet()

        val assets = zipSyncAssetRequests(account, node).blockingGet()

        val assetsLocal = assets.map { it.toLocal(account.address) }

        assetDao.insert(assetsLocal)
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
            Function3<EncodableStruct<AccountInfo>, AssetPriceStatistics, AssetPriceStatistics, List<Asset>> { accountInfo, nowStats, yesterdayStats ->
                listOf(
                    createAsset(account.network.type, accountInfo, nowStats, yesterdayStats)
                )
            })
    }

    private fun createAsset(
        networkType: Node.NetworkType,
        accountInfo: EncodableStruct<AccountInfo>,
        priceStats: AssetPriceStatistics,
        yesterdayPriceStats: AssetPriceStatistics
    ): Asset {
        val balanceInPlanks = accountInfo[data][free]
        val mostRecentPrice = priceStats.content?.price
        val change = priceStats.calculateRateChange(yesterdayPriceStats)

        return Asset(
            Asset.Token.fromNetworkType(networkType),
            balanceInPlanks,
            mostRecentPrice,
            change
        )
    }

    private fun getAssetPrice(networkType: Node.NetworkType, request: AssetPriceRequest): Single<AssetPriceStatistics> {
        return subscanApi.getAssetPrice(subDomainFor(networkType), request)
    }

    private fun subDomainFor(networkType: Node.NetworkType): String {
        return networkType.readableName.toLowerCase(Locale.ROOT)
    }
}