package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import io.mockk.mockk
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.wallet.impl.WalletTestModels
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceLoaderProviderTest {

    private val provider = BalanceLoaderProvider(
        chainRegistry = mockk(), remoteStorageSource = mockk(), ethereumRemoteSource = mockk(), substrateSource = mockk(),
        operationDao = mockk(), tonRemoteSource = mockk(), chainsRepository = mockk(), tonSyncDataRepository = mockk(),
        bitcoinIndexerClient = mockk(), solanaBalanceSync = mockk(), irohaToriiClient = mockk(), chainDao = mockk(relaxed = true),
        scanStateStore = mockk(relaxed = true), bitcoinBalanceSync = mockk(), accountRepository = mockk(), metadataDescriptorStore = mockk()
    )

    @Test
    fun eachChainGetsTheLoaderForItsLedger() {
        val loaders = mapOf(
            WalletTestModels.chain(id = UniversalWalletRegistry.bitcoinMainnet.id) to BitcoinBalanceLoader::class,
            WalletTestModels.chain(id = UniversalWalletRegistry.solanaMainnet.id) to SolanaBalanceLoader::class,
            WalletTestModels.chain(id = UniversalWalletRegistry.taira.id) to IrohaBalanceLoader::class,
            WalletTestModels.chain(id = "-239", ecosystem = Ecosystem.Ton) to TonBalanceLoader::class,
            WalletTestModels.chain(id = "1", ecosystem = Ecosystem.Ethereum) to EthereumBalanceLoader::class,
            WalletTestModels.chain(
                id = "eq",
                assets = listOf(WalletTestModels.asset(id = "eq", chainId = "eq", type = ChainAssetType.Equilibrium))
            ) to EquilibriumBalanceLoader::class,
            WalletTestModels.chain(id = "moonbeam", ecosystem = Ecosystem.EthereumBased) to SubstrateBalanceLoader::class,
            WalletTestModels.chain(id = "polkadot") to SubstrateBalanceLoader::class
        )

        loaders.forEach { (chain, kind) -> assertTrue(chain.id, kind.isInstance(provider.invoke(chain))) }
    }
}
