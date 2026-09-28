package jp.co.soramitsu.runtime.multiNetwork.chain

import android.util.Log
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.model.AssetMetadataDescriptor
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.common.model.AssetMetadataSource
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.ext.assetKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield

class RemoteAssetsSyncServiceProvider(
    //private val okxApiService: OkxApiService,
    private val tonSyncDataRepository: TonSyncDataRepository,
    private val metaAccountDao: MetaAccountDao,
    private val chainDao: ChainDao,
    private val metadataDescriptorStore: AssetMetadataDescriptorStore
){
    fun provide(chain: Chain): RemoteAssetsSyncService? {
        return when {
            chain.isEthereumChain && chain.remoteAssetsSource == Chain.RemoteAssetsSource.OKX -> OkxRemoteAssetsSyncService(chain, chainDao)
            chain.ecosystem == Ecosystem.Ton && chain.remoteAssetsSource == Chain.RemoteAssetsSource.OnChain -> TonRemoteAssetsSyncService(
                chain,
                metaAccountDao,
                tonSyncDataRepository,
                chainDao,
                metadataDescriptorStore
            )
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
    private val chainDao: ChainDao,
    private val metadataDescriptorStore: AssetMetadataDescriptorStore
): RemoteAssetsSyncService {

    companion object {
        private const val TAG = "TonRemoteAssetsSyncService"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun sync() {
        coroutineScope.launch {
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

                                jettonBalances.balances.map { balance ->
                                    metadataDescriptorStore.record(
                                        chain.assetKey(balance.jetton.address),
                                        AssetMetadataDescriptor(
                                            trust = balance.metadataTrust(),
                                            source = AssetMetadataSource.Indexer
                                        )
                                    )
                                    balance.toChainAssetLocal(chain.id)
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
}

internal fun JettonBalance.toChainAssetLocal(chainId: String): ChainAssetLocal {
    val masterAddress = jetton.address
    val verifiedMetadata = jetton.verification.equals("whitelist", ignoreCase = true) ||
        jetton.verification.equals("verified", ignoreCase = true)

    return ChainAssetLocal(
        id = masterAddress,
        name = jetton.name,
        symbol = jetton.symbol,
        chainId = chainId,
        icon = jetton.image,
        // TON prices are trusted only when both metadata and price identity are bound to the
        // canonical Jetton master address. Symbols never become identifiers.
        priceId = masterAddress.takeIf { verifiedMetadata },
        staking = Asset.StakingType.UNSUPPORTED.name,
        precision = jetton.decimals,
        purchaseProviders = null,
        isUtility = false,
        type = ChainAssetType.Jetton.name,
        currencyId = masterAddress,
        existentialDeposit = null,
        color = null,
        isNative = null,
        priceProvider = null,
        coinbaseUrl = null
    )
}

internal fun JettonBalance.metadataTrust(): AssetMetadataTrust {
    val verified = jetton.verification.equals("whitelist", ignoreCase = true) ||
        jetton.verification.equals("verified", ignoreCase = true)
    return if (verified) AssetMetadataTrust.Verified else AssetMetadataTrust.Unverified
}
