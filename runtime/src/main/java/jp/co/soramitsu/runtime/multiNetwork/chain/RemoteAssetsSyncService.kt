package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.data.network.okx.OkxApi
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class RemoteAssetsSyncServiceProvider(
    private val okxApiService: OkxApi,
    private val chainDao: ChainDao
) {
    fun provide(chain: Chain): RemoteAssetsSyncService? {
        return when {
            chain.isEthereumChain && chain.remoteAssetsSource == Chain.RemoteAssetsSource.OKX -> OkxRemoteAssetsSyncService(okxApiService, chain, chainDao)
            else -> null
        }
    }
}

interface RemoteAssetsSyncService {
    suspend fun sync()
}

class OkxRemoteAssetsSyncService(
    private val okxApiService: OkxApi,
    private val chain: Chain,
    private val chainDao: ChainDao
) : RemoteAssetsSyncService {

    override suspend fun sync() {
        println("!!! start sync OkxRemoteAssetsSyncService for chain: ${chain.name} :: ${chain.id}")
        val chainOkxTokens = okxApiService.getAllTokens(chain.id)
        val chainAssets = chainOkxTokens.data.map {
            val utilityAsset = chain.assets.firstOrNull { ca -> ca.isUtility && ca.symbol.equals(it.tokenSymbol, true) }
            @Suppress("IfThenToElvis")
            if (utilityAsset != null) {
                utilityAsset.toLocal(chain.id).copy(
                    currencyId = it.tokenContractAddress
                )
            } else {
                ChainAssetLocal(
                    id = it.tokenContractAddress,
                    name = it.tokenName,
                    symbol = it.tokenSymbol,
                    chainId = chain.id,
                    icon = it.tokenLogoUrl,
                    priceId = null,
                    staking = Asset.StakingType.UNSUPPORTED.name,
                    precision = it.decimals.toInt(),
                    purchaseProviders = null,
                    isUtility = false,
                    type = null,
                    currencyId = it.tokenContractAddress,
                    existentialDeposit = null,
                    color = null,
                    isNative = null,
                    ethereumType = null,
                    priceProvider = null,
                )
            }
        }
        chainDao.insertChainAssets(chainAssets)
    }
}