package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever

class EquilibriumBalanceLoaderTest {

    /**
     * Nothing was read, so nothing is stored: the last known balance stays in the cache. The loader
     * used to store "-1" for every asset, which replaced it with "Balance unavailable".
     */
    @Test
    fun `runtime that does not load in time stores no update over the last known balance`() = runTest {
        val chain = equilibriumChain()
        val chainRegistry = mock(ChainRegistry::class.java)
        whenever(chainRegistry.checkChainSyncedUp(chain)).thenReturn(true)
        whenever(chainRegistry.awaitRuntimeProvider(CHAIN_ID)).doSuspendableAnswer { awaitCancellation() }
        val remoteStorageSource = mock(RemoteStorageSource::class.java)

        val updates = EquilibriumBalanceLoader(chain, chainRegistry, remoteStorageSource).loadBalance(setOf(account()))

        assertEquals(emptyList<Any>(), updates)
        verify(remoteStorageSource, never()).queryKeys(any(), any(), anyOrNull())
    }

    private fun account() = MetaAccount(
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
        name = "Equilibrium wallet",
        initialized = true
    )

    private fun equilibriumChain() = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = null,
        name = "Equilibrium",
        minSupportedVersion = null,
        assets = listOf(asset("eq", currencyId = "25969", isUtility = true), asset("eqd", currencyId = "6648164", isUtility = false)),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = Chain.ExternalApi(staking = null, history = null, crowdloans = null),
        icon = "",
        addressPrefix = 68,
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

    private fun asset(id: String, currencyId: String, isUtility: Boolean) = Asset(
        id = id,
        name = id.uppercase(),
        symbol = id.uppercase(),
        iconUrl = "",
        chainId = CHAIN_ID,
        chainName = "Equilibrium",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 9,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = isUtility,
        type = ChainAssetType.Equilibrium,
        currencyId = currencyId,
        existentialDeposit = null,
        color = null,
        isNative = isUtility
    )

    private companion object {
        const val CHAIN_ID = "equilibrium-mainnet"
    }
}
