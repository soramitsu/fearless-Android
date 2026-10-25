package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.config.FeatureToggleConfig
import jp.co.soramitsu.common.data.network.config.MutationAuthorizationStore
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.di.WalletFeatureModule
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.SendPausedException
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal

class SendPauseTransferGuardTest {

    private var bitcoinSendEnabled = true
    private var solanaSendEnabled = true
    private val policy = SendPausePolicy(
        bitcoinSendEnabled = { bitcoinSendEnabled },
        solanaSendEnabled = { solanaSendEnabled },
        pausedErrorFactory = { chain -> SendPausedException("Sending paused", "Sending on ${chain.name} is paused") }
    )

    private val bitcoinIndexerClient = mock(BitcoinIndexerClient::class.java)
    private val solanaRpcClient = mock(SolanaRpcClient::class.java)
    private val accountRepository = mock(AccountRepository::class.java)

    @Test
    fun `sends are enabled for every network without a veto`() {
        assertFalse(policy.isPaused(bitcoinChain()))
        assertFalse(policy.isPaused(solanaChain()))
        assertFalse(policy.isPaused(tonChain()))
    }

    @Test
    fun `bitcoin veto pauses only bitcoin`() {
        bitcoinSendEnabled = false

        assertTrue(policy.isPaused(bitcoinChain()))
        assertTrue(policy.isPaused(bitcoinChain(UniversalWalletRegistry.bitcoinTestnet)))
        assertFalse(policy.isPaused(solanaChain()))
        assertFalse(policy.isPaused(tonChain()))
    }

    @Test
    fun `solana veto pauses only solana`() {
        solanaSendEnabled = false

        assertTrue(policy.isPaused(solanaChain()))
        assertFalse(policy.isPaused(bitcoinChain()))
    }

    @Test
    fun `paused switch throws before any network call`() {
        bitcoinSendEnabled = false
        solanaSendEnabled = false

        for (chain in listOf(bitcoinChain(), solanaChain())) {
            val service = provider().provide(chain)
            val transfer = Transfer("sender", "recipient", BigDecimal.ONE, chain.assets.single())

            val feeFailure = assertThrows(SendPausedException::class.java) {
                runBlocking { service.getTransferFee(transfer) }
            }
            assertThrows(SendPausedException::class.java) {
                runBlocking { service.observeTransferFee(transfer).first() }
            }
            assertThrows(SendPausedException::class.java) {
                runBlocking { service.transfer(transfer) }
            }
            assertEquals("Sending paused", feeFailure.title)
            assertEquals("Sending on ${chain.name} is paused", feeFailure.message)
        }

        verifyNoInteractions(bitcoinIndexerClient, solanaRpcClient, accountRepository)
    }

    @Test
    fun `lifting the veto restores the real send services`() {
        bitcoinSendEnabled = false
        val provider = provider()
        assertTrue(provider.provide(bitcoinChain()) is PausedTransferService)

        bitcoinSendEnabled = true

        assertTrue(provider.provide(bitcoinChain()) is BitcoinTransferService)
        assertTrue(provider.provide(solanaChain()) is SolanaTransferService)
    }

    @Test
    fun `default provider policy never pauses`() {
        val provider = provider(SendPausePolicy.Unrestricted)

        assertTrue(provider.provide(bitcoinChain()) is BitcoinTransferService)
        assertTrue(provider.provide(solanaChain()) is SolanaTransferService)
    }

    @Test
    fun `production policy follows the remote veto and explains it in localized copy`() {
        val toggles = ProductFeatureToggleStore(InMemoryPreferences(), mock(MutationAuthorizationStore::class.java))
        val resources = mock(ResourceManager::class.java)
        `when`(resources.getString(R.string.paused_send_title)).thenReturn("Sending paused")
        `when`(resources.getString(R.string.paused_send_message, "Bitcoin")).thenReturn("Sending on Bitcoin is paused")
        val productionPolicy = WalletFeatureModule().provideSendPausePolicy(toggles, resources)
        val bitcoin = bitcoinChain()

        // Shipped enabled: no veto, no paused copy.
        assertFalse(productionPolicy.isPaused(bitcoin))
        assertFalse(productionPolicy.isPaused(solanaChain()))

        toggles.update(FeatureToggleConfig(bitcoinSendEnabled = false))

        assertTrue(productionPolicy.isPaused(bitcoin))
        assertFalse(productionPolicy.isPaused(solanaChain()))
        val error = productionPolicy.pausedError(bitcoin)
        assertEquals("Sending paused", error.title)
        assertEquals("Sending on Bitcoin is paused", error.message)
    }

    private fun provider(sendPausePolicy: SendPausePolicy = policy) = TransferServiceProvider(
        substrateSource = mock(SubstrateRemoteSource::class.java),
        ethereumRemoteSource = mock(EthereumRemoteSource::class.java),
        keyPairRepository = mock(KeypairProvider::class.java),
        accountRepository = accountRepository,
        tonRemoteSource = mock(TonRemoteSource::class.java),
        assetDao = mock(AssetDao::class.java),
        bitcoinIndexerClient = bitcoinIndexerClient,
        solanaRpcClient = solanaRpcClient,
        solanaBalanceSync = mock(SolanaBalanceSync::class.java),
        irohaToriiClient = mock(IrohaToriiClient::class.java),
        sendPausePolicy = sendPausePolicy,
        sendJournal = jp.co.soramitsu.common.data.network.send.WalletSendJournal(mock(jp.co.soramitsu.common.data.storage.Preferences::class.java))
    )

    private fun bitcoinChain(
        network: UniversalWalletRegistry.BitcoinNetwork = UniversalWalletRegistry.bitcoinMainnet
    ) = chain(network.id, network.name, "BTC", Chain.ExternalApi.Section.Type.BITCOIN, network.indexerBaseUrl)

    private fun solanaChain() = UniversalWalletRegistry.solanaMainnet.let { network ->
        chain(network.id, network.name, "SOL", Chain.ExternalApi.Section.Type.SOLANA, network.indexerBaseUrl)
    }

    private fun tonChain() = chain(
        "ton-mainnet",
        "TON",
        "TON",
        Chain.ExternalApi.Section.Type.TON,
        UniversalWalletRegistry.TON_INDEXER_BASE_URL,
        Ecosystem.Ton
    )

    private fun chain(
        id: String,
        name: String,
        symbol: String,
        historyType: Chain.ExternalApi.Section.Type,
        historyUrl: String,
        ecosystem: Ecosystem = Ecosystem.Substrate
    ) = Chain(
        id = id,
        paraId = null,
        rank = null,
        name = name,
        minSupportedVersion = null,
        assets = listOf(
            Asset(
                id = symbol,
                name = name,
                symbol = symbol,
                iconUrl = "",
                chainId = id,
                chainName = name,
                chainIcon = null,
                isTestNet = false,
                priceId = null,
                precision = 8,
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
        ),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = Chain.ExternalApi(
            staking = null,
            history = Chain.ExternalApi.Section(historyType, historyUrl),
            crowdloans = null
        ),
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
        ecosystem = ecosystem,
        remoteAssetsSource = null
    )
}
