package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock

class SubstrateBalanceLoaderTest {

    @Test
    fun `runtime endpoint failure emits no zero and retains stale last success`() = runBlocking {
        val chain = substrateChain()
        val account = substrateAccount()
        val chainRegistry = mock(ChainRegistry::class.java)
        val scanStateStore = NetworkScanStateStore(InMemoryPreferences()).apply {
            scanSucceeded(
                account.id,
                chain.id,
                AssetDiscoveryCoverage.CatalogOnly,
                succeededAtMillis = 10
            )
        }
        doThrow(IllegalStateException("substrate endpoint down"))
            .`when`(chainRegistry)
            .checkChainSyncedUp(chain)

        val loader = SubstrateBalanceLoader(
            chain = chain,
            chainRegistry = chainRegistry,
            remoteStorageSource = mock(RemoteStorageSource::class.java),
            substrateSource = mock(SubstrateRemoteSource::class.java),
            operationDao = mock(OperationDao::class.java),
            scanStateStore = scanStateStore
        )

        val updates = loader.loadBalance(setOf(account))

        assertTrue(updates.isEmpty())
        val state = scanStateStore.states.value.getValue(NetworkScanKey(account.id, chain.id))
        assertEquals(10L, state.lastSuccessMillis)
        assertTrue(state.isStale)
        assertEquals("substrate endpoint down", state.errorMessage)
    }

    private fun substrateAccount() = MetaAccount(
        id = 1,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = ByteArray(32) { 2 },
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Substrate wallet",
        initialized = true
    )

    private fun substrateChain() = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = null,
        name = "Substrate",
        minSupportedVersion = null,
        assets = listOf(substrateAsset()),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = Chain.ExternalApi(staking = null, history = null, crowdloans = null),
        icon = "",
        addressPrefix = 42,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        remoteAssetsSource = null
    )

    private fun substrateAsset() = Asset(
        id = "UNIT",
        name = "Unit",
        symbol = "UNIT",
        iconUrl = "",
        chainId = CHAIN_ID,
        chainName = "Substrate",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 12,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = true,
        type = ChainAssetType.Normal,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = true
    )

    private companion object {
        const val CHAIN_ID = "substrate-mainnet"
    }
}
