package jp.co.soramitsu.runtime.multiNetwork.chain

import android.util.Log
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield

class RemoteAssetsSyncServiceProvider(
    //private val okxApiService: OkxApiService,
    private val tonSyncDataRepository: TonSyncDataRepository,
    private val metaAccountDao: MetaAccountDao,
    private val chainDao: ChainDao
){
    fun provide(chain: Chain): RemoteAssetsSyncService? {
        return when {
            chain.isEthereumChain && chain.remoteAssetsSource == Chain.RemoteAssetsSource.OKX -> OkxRemoteAssetsSyncService(chain, chainDao)
            chain.ecosystem == Ecosystem.Ton && chain.remoteAssetsSource == Chain.RemoteAssetsSource.OnChain -> TonRemoteAssetsSyncService(chain, metaAccountDao, tonSyncDataRepository, chainDao)
            else -> null
        }
    }
}

interface RemoteAssetsSyncService {
    suspend fun sync()
}

class OkxRemoteAssetsSyncService(
    //private val okxApiService: OkxApiService,
    private val chain: Chain,
    private val chainDao: ChainDao
): RemoteAssetsSyncService {

    override suspend fun sync() {
        //val assets = okxApiService.getAvailableAssets()
        //val chainAssets = assets.map { ChainAssetLocal(id = "", name = "", chainId = chain.id, ...) }
        //chainDao.insertChainAssets(chainAssets)
    }

}

class TonRemoteAssetsSyncService(
    private val chain: Chain,
    private val metaAccountDao: MetaAccountDao,
    private val tonSyncDataRepository: TonSyncDataRepository,
    private val chainDao: ChainDao
): RemoteAssetsSyncService {

    companion object {
        private const val TAG = "TonRemoteAssetsSyncService"
    }

    override suspend fun sync() {
        metaAccountDao.metaAccountsFlow()
            .map { accounts -> accounts.mapNotNull { it.tonPublicKey } }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .collect { publicKeys ->
                supervisorScope {
                    val chainAssetsDeferred = publicKeys.map { publicKey ->
                        async {
                            val accountId = publicKey.tonAccountId(chain.isTestNet)
                            val jettonBalances = kotlin.runCatching {
                                tonSyncDataRepository.getJettonBalances(chain, accountId)
                            }.onFailure { Log.d(TAG, "Failed load jetton balances: $it") }.getOrNull() ?: return@async emptyList()

                            yield()

                            jettonBalances.balances.map { jettonBalance ->
                                ChainAssetLocal(
                                    id = jettonBalance.jetton.address,
                                    name = jettonBalance.jetton.name,
                                    symbol = jettonBalance.jetton.symbol,
                                    chainId = chain.id,
                                    icon = jettonBalance.jetton.image,
                                    priceId = jettonBalance.jetton.symbol,
                                    staking = Asset.StakingType.UNSUPPORTED.name,
                                    precision = jettonBalance.jetton.decimals,
                                    purchaseProviders = null,
                                    isUtility = false,
                                    type = ChainAssetType.Jetton.name,
                                    currencyId = null,
                                    existentialDeposit = null,
                                    color = null,
                                    isNative = null,
                                    priceProvider = null
                                )
                            }
                        }
                    }
                    val chainAssets = chainAssetsDeferred.awaitAll().flatten()

                    yield()

                    chainDao.insertChainAssetsIgnoringConflicts(chainAssets)
                }
            }
    }
}