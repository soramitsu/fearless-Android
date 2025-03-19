package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource

class BalanceLoaderProvider(
    private val chainRegistry: ChainRegistry,
    private val remoteStorageSource: RemoteStorageSource,
    private val ethereumRemoteSource: EthereumRemoteSource,
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val tonRemoteSource: TonRemoteSource,
    private val chainsRepository: ChainsRepository,
    private val tonSyncDataRepository: TonSyncDataRepository,
): BalanceLoader.Provider {

    override fun invoke(chain: Chain): BalanceLoader {
        val isEquilibriumTypeChain = chain.utilityAsset != null && chain.utilityAsset!!.typeExtra == ChainAssetType.Equilibrium

        return when {
            chain.ecosystem == Ecosystem.Ton -> TonBalanceLoader(chain, tonSyncDataRepository, chainsRepository)
            chain.ecosystem == Ecosystem.Ethereum -> EthereumBalanceLoader(chain, ethereumRemoteSource)

            chain.ecosystem == Ecosystem.EthereumBased
                    || chain.ecosystem == Ecosystem.Substrate -> SubstrateBalanceLoader(chain, chainRegistry, remoteStorageSource, substrateSource, operationDao)
            isEquilibriumTypeChain -> EquilibriumBalanceLoader(chain, chainRegistry, remoteStorageSource)
            else -> throw IllegalStateException("Cannot find BalanceLoader for ${chain.name}")
        }
    }
}