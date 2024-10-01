package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.data.network.okx.OkxApi
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlin.math.min

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
        val chainOkxTokens = okxApiService.getAllTokens(chain.id).data
            .filter {
                when (chain.id) {
                    // Ethereum
                    "1" -> it.tokenSymbol.lowercase() in listOf("eth", "dai", "usdt", "usdc", "crv", "uni", "link", "1inch", "bnb", "matic", "xor", "val", "pswap", "ceres", "cru", "sand", "flt")
                    // BNB Smart Chain
                    "56" -> it.tokenSymbol.lowercase() in listOf("bnb", "dai", "eth", "usdc", "busd", "tusd", "matic", "cake", "bsw", "hook", "xvs")
                    // Polygon
                    "137" -> it.tokenSymbol.lowercase() in listOf("matic", "weth", "usdc", "usdt", "bnb", "busd", "dai", "aave", "crv", "uni")
                    // Arbitrum One
                    "42161" -> it.tokenSymbol.lowercase() in listOf("eth", "arb")
                    // OP Mainnet
                    "10" -> it.tokenSymbol.lowercase() in listOf("eth", "op")
                    // Avalanche C-Chain
                    "43114" -> it.tokenSymbol.lowercase() in listOf("avax")
                    // X Layer Testnet
                    "195" -> it.tokenSymbol.lowercase() in listOf("okb")
                    // X Layer Mainnet
                    "196" -> it.tokenSymbol.lowercase() in listOf("okb")
                    // Polygon zkEVM
                    "1101" -> it.tokenSymbol.lowercase() in listOf("eth", "usdc", "matic")

                    else -> true
                }
            }


        val localChainAssets = chainDao.getAssetsConfigs(chain.id)

        val remoteChainAssets = chainOkxTokens.mapNotNull {
            if (it.tokenContractAddress.startsWith("0x").not()) {
                println("!!! broken asset: ${it.tokenName}:${it.tokenSymbol} - ${it.tokenContractAddress}")
                return@mapNotNull null
            }
            val utilityAsset = chain.assets.firstOrNull { ca -> ca.isUtility && ca.symbol.equals(it.tokenSymbol, true) }
            @Suppress("IfThenToElvis")
            if (utilityAsset != null) {
                utilityAsset.toLocal(chain.id)
                    .copy(currencyId = it.tokenContractAddress)
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
                    type = ChainAssetType.Normal.name,
                    currencyId = it.tokenContractAddress,
                    existentialDeposit = null,
                    color = null,
                    isNative = null,
                    ethereumType = null,
                    priceProvider = null,
                )
            }
        }

        val assetsToAdd: MutableList<ChainAssetLocal> = mutableListOf()
        val assetsToUpdate: MutableList<ChainAssetLocal> = mutableListOf()

        val remoteAssetsIds = remoteChainAssets.map { it.id }
        val assetsToRemove = localChainAssets.filter {
            it.isUtility != true && it.id !in remoteAssetsIds
        }

        remoteChainAssets.forEach { remoteAsset ->
            val localAsset = localChainAssets.find { it.id == remoteAsset.id }

            when {
                localAsset == null -> {
                    assetsToAdd.add(remoteAsset)
                } // new
                localAsset != remoteAsset -> {
                    assetsToUpdate.add(remoteAsset)
                } // updated
            }
        }
        println("!!! RASS: chain ${chain.name}:${chain.id} assetsToAdd: ${assetsToAdd.size}; update ${assetsToUpdate.size}; remove ${assetsToRemove.size}:${assetsToRemove.subList(0, min(assetsToRemove.size, 5)).joinToString { it.symbol }}")
        chainDao.updateAssets(assetsToAdd, assetsToUpdate, assetsToRemove)
    }
}