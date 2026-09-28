package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

class EthereumBalanceLoaderTest {

    @Test
    fun `endpoint failure preserves last EVM amount and marks scan stale`() = runBlocking {
        val asset = evmAsset()
        val chain = evmChain(asset)
        val account = evmAccount()
        val address = requireNotNull(account.address(chain))
        val remoteSource = mock(EthereumRemoteSource::class.java)
        val scanStateStore = NetworkScanStateStore(InMemoryPreferences())
        var endpointFailure: Throwable? = null

        doAnswer {
            endpointFailure?.let { throw it }
            BigInteger.valueOf(55)
        }.`when`(remoteSource).fetchEthBalance(asset, address)

        val loader = EthereumBalanceLoader(chain, remoteSource, scanStateStore)
        var storedAmount = loader.loadBalance(setOf(account)).single().freeInPlanks

        endpointFailure = IllegalStateException("evm endpoint down")
        loader.loadBalance(setOf(account)).singleOrNull()?.let {
            storedAmount = it.freeInPlanks
        }

        assertEquals(BigInteger.valueOf(55), storedAmount)
        val state = scanStateStore.states.value.getValue(NetworkScanKey(account.id, chain.id))
        assertTrue(state.isStale)
        assertTrue(state.lastSuccessMillis != null)
        assertEquals("evm endpoint down", state.errorMessage)
    }

    private fun evmAccount() = MetaAccount(
        id = 1,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = null,
        substrateCryptoType = null,
        substrateAccountId = null,
        ethereumAddress = ByteArray(20) { 1 },
        ethereumPublicKey = ByteArray(33) { 2 },
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "EVM wallet",
        initialized = true
    )

    private fun evmChain(asset: Asset) = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = null,
        name = "Ethereum",
        minSupportedVersion = null,
        assets = listOf(asset),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = Chain.ExternalApi(staking = null, history = null, crowdloans = null),
        icon = "",
        addressPrefix = 0,
        isEthereumBased = true,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = true,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.EthereumBased,
        remoteAssetsSource = null
    )

    private fun evmAsset() = Asset(
        id = "ETH",
        name = "Ether",
        symbol = "ETH",
        iconUrl = "",
        chainId = CHAIN_ID,
        chainName = "Ethereum",
        chainIcon = null,
        isTestNet = false,
        priceId = "ethereum",
        precision = 18,
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
        const val CHAIN_ID = "evm-mainnet"
    }
}
