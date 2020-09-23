package jp.co.soramitsu.feature_wallet_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.data.network.source.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.toAsset
import jp.co.soramitsu.feature_wallet_impl.data.toLocal
import java.math.BigDecimal

class WalletRepositoryImpl(
    private val substrateSource: WssSubstrateSource,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao
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
        val accountInfo = substrateSource.fetchAccountInfo(account, node).blockingGet()

        val assets = listOf(createAsset(account.network.type, accountInfo))
        val assetsLocal = assets.map { it.toLocal(account.address) }

        assetDao.insert(assetsLocal)
    }

    private fun createAsset(networkType: Node.NetworkType, accountInfo: EncodableStruct<AccountInfo>): Asset {
        val balanceInPlanks = accountInfo[data][free]

        return Asset(
            Asset.Token.fromNetworkType(networkType),
            balanceInPlanks,
            BigDecimal(10.0),
            BigDecimal(10.0)
        )
    }
}