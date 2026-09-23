package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TonBalanceLoaderTest {

    @Test
    fun `discovers held jettons by master address without merging duplicate symbols`() = runBlocking {
        val chain = tonChain()
        val account = tonAccount()
        val repository = mock(TonSyncDataRepository::class.java)
        val chainsRepository = mock(ChainsRepository::class.java)
        `when`(chainsRepository.getChain(chain.id)).thenReturn(chain)
        `when`(repository.getAccountData(chain, account.tonAccountId())).thenReturn(accountData(90))
        `when`(repository.getJettonBalances(chain, account.tonAccountId())).thenReturn(
            JettonsBalances(
                listOf(
                    jetton("master-a", "DUP", "whitelist", "2"),
                    jetton("master-b", "DUP", "none", "7")
                )
            )
        )
        val discovered = mutableListOf<Asset>()

        val updates = TonBalanceLoader(
            chain = chain,
            tonSyncDataRepository = repository,
            chainsRepository = chainsRepository,
            persistDiscoveredAssets = { discovered += it }
        ).loadBalance(setOf(account))

        assertEquals(setOf("TON", "master-a", "master-b"), updates.map { it.id }.toSet())
        assertEquals(BigInteger.valueOf(2), updates.single { it.id == "master-a" }.freeInPlanks)
        assertEquals(BigInteger.valueOf(7), updates.single { it.id == "master-b" }.freeInPlanks)
        assertEquals(setOf("master-a", "master-b"), discovered.map { it.id }.toSet())
        assertEquals("master-a", discovered.single { it.id == "master-a" }.priceId)
        assertNull(discovered.single { it.id == "master-b" }.priceId)
        assertTrue(discovered.all { it.type == ChainAssetType.Jetton })
    }

    @Test
    fun `endpoint failure emits no replacement zero and marks last balance stale`() = runBlocking {
        val chain = tonChain()
        val account = tonAccount()
        val repository = mock(TonSyncDataRepository::class.java)
        val chainsRepository = mock(ChainsRepository::class.java)
        val scanState = NetworkScanStateStore(InMemoryPreferences())
        `when`(chainsRepository.getChain(chain.id)).thenReturn(chain)
        `when`(repository.getAccountData(chain, account.tonAccountId()))
            .thenReturn(accountData(55))
            .thenThrow(IllegalStateException("TON endpoint down"))
        `when`(repository.getJettonBalances(chain, account.tonAccountId()))
            .thenReturn(JettonsBalances(emptyList()))
            .thenThrow(IllegalStateException("TON endpoint down"))
        val loader = TonBalanceLoader(chain, repository, chainsRepository, scanStateStore = scanState)

        var storedAmount = loader.loadBalance(setOf(account)).single { it.id == "TON" }.freeInPlanks
        loader.loadBalance(setOf(account)).singleOrNull { it.id == "TON" }?.let {
            storedAmount = it.freeInPlanks
        }

        assertEquals(BigInteger.valueOf(55), storedAmount)
        val state = scanState.states.value.getValue(NetworkScanKey(account.id, chain.id))
        assertTrue(state.isStale)
        assertTrue(state.lastSuccessMillis != null)
        assertEquals("TON endpoint down", state.errorMessage)
    }

    private fun MetaAccount.tonAccountId(): String =
        requireNotNull(tonPublicKey).tonAccountId(false)

    private companion object {
        fun tonAccount() = MetaAccount(
            id = 1,
            chainAccounts = emptyMap(),
            favoriteChains = emptyMap(),
            substratePublicKey = null,
            substrateCryptoType = CryptoType.SR25519,
            substrateAccountId = null,
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = ByteArray(32) { (it + 1).toByte() },
            isSelected = true,
            isBackedUp = true,
            googleBackupAddress = null,
            name = "TON wallet",
            initialized = true
        )

        fun tonChain() = Chain(
            id = "-239",
            paraId = null,
            rank = null,
            name = "TON",
            minSupportedVersion = null,
            assets = listOf(nativeTonAsset()),
            nodes = emptyList(),
            explorers = emptyList(),
            externalApi = null,
            icon = "",
            addressPrefix = 0,
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
            ecosystem = Ecosystem.Ton,
            remoteAssetsSource = Chain.RemoteAssetsSource.OnChain
        )

        fun nativeTonAsset() = Asset(
            id = "TON",
            name = "Toncoin",
            symbol = "TON",
            iconUrl = "",
            chainId = "-239",
            chainName = "TON",
            chainIcon = null,
            isTestNet = false,
            priceId = "the-open-network",
            precision = 9,
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

        fun accountData(balance: Long) = TonAccountData(
            address = "wallet",
            balance = balance,
            lastActivity = 0,
            status = AccountStatus.active,
            getMethods = emptyList(),
            isWallet = true
        )

        fun jetton(
            masterAddress: String,
            symbol: String,
            verification: String,
            balance: String
        ) = JettonBalance(
            balance = balance,
            walletAddress = AccountAddress(masterAddress, false, true),
            jetton = JettonPreview(
                address = masterAddress,
                name = symbol,
                symbol = symbol,
                decimals = 9,
                image = "",
                verification = verification
            )
        )
    }
}
