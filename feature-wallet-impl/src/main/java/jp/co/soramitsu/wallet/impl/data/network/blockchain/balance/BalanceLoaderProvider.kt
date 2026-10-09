package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinBalanceSync
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
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
    private val bitcoinIndexerClient: BitcoinIndexerClient,
    private val solanaBalanceSync: SolanaBalanceSync,
    private val irohaToriiClient: IrohaToriiClient,
    private val chainDao: ChainDao? = null,
    private val scanStateStore: NetworkScanStateStore? = null,
    private val bitcoinBalanceSync: BitcoinBalanceSync? = null,
    private val accountRepository: AccountRepository? = null,
    private val metadataDescriptorStore: AssetMetadataDescriptorStore? = null
): BalanceLoader.Provider {

    override fun invoke(chain: Chain): BalanceLoader {
        val isEquilibriumTypeChain = chain.utilityAsset != null && chain.utilityAsset!!.typeExtra == ChainAssetType.Equilibrium

        return when {
            chain.isUniversalWalletBitcoin() -> BitcoinBalanceLoader(
                chain,
                bitcoinIndexerClient,
                bitcoinBalanceSync,
                accountRepository,
                scanStateStore
            )
            chain.isUniversalWalletSolana() -> SolanaBalanceLoader(
                chain,
                solanaBalanceSync,
                persistDiscoveredAssets = ::persistDiscoveredAssets,
                scanStateStore = scanStateStore
            )
            chain.isUniversalWalletIroha() -> IrohaBalanceLoader(
                chain,
                irohaToriiClient,
                persistDiscoveredAssets = ::persistDiscoveredAssets,
                scanStateStore = scanStateStore
            )
            chain.ecosystem == Ecosystem.Ton -> TonBalanceLoader(
                chain,
                tonSyncDataRepository,
                chainsRepository,
                persistDiscoveredAssets = ::persistDiscoveredAssets,
                metadataDescriptorStore = metadataDescriptorStore,
                scanStateStore = scanStateStore
            )
            chain.ecosystem == Ecosystem.Ethereum -> EthereumBalanceLoader(chain, ethereumRemoteSource, scanStateStore)
            isEquilibriumTypeChain -> EquilibriumBalanceLoader(chain, chainRegistry, remoteStorageSource)

            chain.ecosystem == Ecosystem.EthereumBased
                    || chain.ecosystem == Ecosystem.Substrate -> SubstrateBalanceLoader(
                chain,
                chainRegistry,
                remoteStorageSource,
                substrateSource,
                operationDao,
                scanStateStore
            )
            else -> throw IllegalStateException("Cannot find BalanceLoader for ${chain.name}")
        }
    }

    private suspend fun persistDiscoveredAssets(assets: List<jp.co.soramitsu.core.models.Asset>) {
        if (assets.isEmpty()) return
        chainDao?.insertChainAssetsIgnoringConflicts(assets.map { it.toLocal() })
    }

    private fun jp.co.soramitsu.core.models.Asset.toLocal() = ChainAssetLocal(
        id = id,
        name = name,
        symbol = symbol,
        chainId = chainId,
        icon = iconUrl,
        priceId = priceId,
        staking = staking.name,
        precision = precision,
        purchaseProviders = null,
        isUtility = false,
        // Dynamic loaders preserve their network-specific detected type (Jetton or Unknown).
        type = type?.name ?: ChainAssetType.Unknown.name,
        currencyId = currencyId,
        existentialDeposit = null,
        color = null,
        isNative = isNative,
        priceProvider = null,
        coinbaseUrl = null
    )
}
