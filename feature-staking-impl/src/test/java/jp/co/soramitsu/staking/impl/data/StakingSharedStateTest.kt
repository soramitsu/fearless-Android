package jp.co.soramitsu.staking.impl.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Asset.StakingType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.givenBlocking
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.seconds

@RunWith(MockitoJUnitRunner::class)
class StakingSharedStateTest {

    @Mock
    private lateinit var chainRegistry: ChainRegistry
    @Mock
    private lateinit var preferences: Preferences
    @Mock
    private lateinit var walletRepository: WalletRepository
    @Mock
    private lateinit var accountRepository: AccountRepository
    @Mock
    private lateinit var chainsRepository: ChainsRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var stakingSharedState: StakingSharedState

    private val testChainId = "test_chain"
    private val testAssetId = "test_asset"
    private val metaAccountId = 1L

    private lateinit var metaAccount: MetaAccount
    private lateinit var chainAsset: jp.co.soramitsu.core.models.Asset
    private lateinit var chain: Chain
    private lateinit var token: Token
    private lateinit var asset: Asset
    private lateinit var metaAccountFlow: MutableStateFlow<MetaAccount>

    @Before
    fun setup() = runTest(testDispatcher, timeout = 5.seconds) {
        chainAsset = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(staking).willReturn(StakingType.RELAYCHAIN)
            given(supportStakingPool).willReturn(true)
        }

        chain = mock(Chain::class.java).apply {
            given(id).willReturn(testChainId)
            given(assets).willReturn(listOf(chainAsset))
            given(ecosystem).willReturn(Ecosystem.Substrate)
        }


        metaAccount = mock(MetaAccount::class.java).apply {
            given(id).willReturn(metaAccountId)
        }

        given(metaAccount.accountId(chain)).willReturn(ByteArray(32))


        val tokenConfiguration = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(chainId).willReturn(testChainId)
        }

        token = mock(Token::class.java).apply {
            given(configuration).willReturn(tokenConfiguration)
        }

        asset = mock(Asset::class.java).apply {
            given(token).willReturn(this@StakingSharedStateTest.token)
            given(chainId).willReturn(testChainId)
            given(id).willReturn(testAssetId)
        }

        metaAccountFlow = MutableStateFlow(metaAccount)

        given(accountRepository.selectedMetaAccountFlow()).willReturn(metaAccountFlow)
        givenBlocking { chainsRepository.getChains() }.willReturn(listOf(chain))
        givenBlocking { walletRepository.getAssets(metaAccountId) }.willReturn(listOf(asset))

        val initialSelection = StakingAssetSelection.RelayChainStaking(testChainId, testAssetId)
        val encodedSelection = "${initialSelection.chainId}:${initialSelection.chainAssetId}:${initialSelection.type.name}"
        whenever(preferences.stringFlow(any(), any())).thenReturn(flowOf(encodedSelection))

        stakingSharedState = StakingSharedState(
            chainRegistry = chainRegistry,
            preferences = preferences,
            walletRepository = walletRepository,
            accountRepository = accountRepository,
            chainsRepository = chainsRepository,
            scope = testScope
        )

        stakingSharedState.selectionItem.first()
        stakingSharedState.assetWithChain.first()
        stakingSharedState.currentAssetFlow().first()
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `should return cached staking assets`() = runTest(testDispatcher, timeout = 5.seconds) {
        val cachedAssets = stakingSharedState.availableAssetsToSelect()

        assertEquals(1, cachedAssets.size)
        assertEquals(testAssetId, cachedAssets.first().id)
        assertEquals(testChainId, cachedAssets.first().chainId)
    }

    @Test
    fun `should return available staking selections`() = runTest(testDispatcher, timeout = 5.seconds) {
        val selections = stakingSharedState.availableToSelect()

        assertEquals(2, selections.size) // One for RelayChain and one for Pool
        
        val relayChainSelection = selections.find { it is StakingAssetSelection.RelayChainStaking }
        assertNotNull(relayChainSelection)
        assertEquals(testChainId, relayChainSelection?.chainId)
        assertEquals(testAssetId, relayChainSelection?.chainAssetId)

        val poolSelection = selections.find { it is StakingAssetSelection.Pool }
        assertNotNull(poolSelection)
        assertEquals(testChainId, poolSelection?.chainId)
        assertEquals(testAssetId, poolSelection?.chainAssetId)
    }

    @Test
    fun `should update selection item`() = runTest(testDispatcher, timeout = 5.seconds) {
        val selection = StakingAssetSelection.RelayChainStaking(testChainId, testAssetId)
        stakingSharedState.update(selection)

        val currentSelection = stakingSharedState.selectionItem.first()
        assertEquals(selection.chainId, currentSelection.chainId)
        assertEquals(selection.chainAssetId, currentSelection.chainAssetId)
    }

    @Test
    fun `should return current asset`() = runTest(testDispatcher, timeout = 5.seconds) {
        val currentAsset = stakingSharedState.currentAssetFlow().first()
        
        assertEquals(testAssetId, currentAsset.id)
        assertEquals(testChainId, currentAsset.chainId)
    }

    @Test
    fun `should return current chain`() = runTest(testDispatcher, timeout = 5.seconds) {
        val currentChain = stakingSharedState.chain()
        
        assertEquals(testChainId, currentChain.id)
    }

    @Test
    fun `should return current token`() = runTest(testDispatcher, timeout = 5.seconds) {
        val currentToken = stakingSharedState.currentToken()
        
        assertEquals(testAssetId, currentToken.configuration.id)
        assertEquals(testChainId, currentToken.configuration.chainId)
    }

    @Test
    fun `should handle empty assets list`() = runTest(testDispatcher, timeout = 5.seconds) {
        givenBlocking { walletRepository.getAssets(metaAccountId) }.willReturn(emptyList())
        
        val newStakingSharedState = StakingSharedState(
            chainRegistry = chainRegistry,
            preferences = preferences,
            walletRepository = walletRepository,
            accountRepository = accountRepository,
            chainsRepository = chainsRepository,
            scope = testScope
        )
        
        val cachedAssets = newStakingSharedState.availableAssetsToSelect()
        assertTrue(cachedAssets.isEmpty())
    }

    @Test
    fun `should handle empty chains list`() = runTest(testDispatcher, timeout = 5.seconds) {
        givenBlocking { chainsRepository.getChains() }.willReturn(emptyList())
        
        val newStakingSharedState = StakingSharedState(
            chainRegistry = chainRegistry,
            preferences = preferences,
            walletRepository = walletRepository,
            accountRepository = accountRepository,
            chainsRepository = chainsRepository,
            scope = testScope
        )
        
        val selections = newStakingSharedState.availableToSelect()
        assertTrue(selections.isEmpty())
    }

    @Test
    fun `should handle chain with no staking assets`() = runTest(testDispatcher, timeout = 5.seconds) {
        val nonStakingChainAsset = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(staking).willReturn(StakingType.UNSUPPORTED)
        }
        
        val chainWithoutStaking = mock(Chain::class.java).apply {
            given(id).willReturn("non_staking_chain")
            given(assets).willReturn(listOf(nonStakingChainAsset))
            given(ecosystem).willReturn(Ecosystem.Substrate)
        }

        // Create new meta account for this test
        val testMetaAccount = mock(MetaAccount::class.java).apply {
            given(id).willReturn(metaAccountId)
        }
        given(testMetaAccount.accountId(chainWithoutStaking)).willReturn(ByteArray(32))
        
        val testMetaAccountFlow = MutableStateFlow(testMetaAccount)
        given(accountRepository.selectedMetaAccountFlow()).willReturn(testMetaAccountFlow)
        
        // Update mocks with new chain
        givenBlocking { chainsRepository.getChains() }.willReturn(listOf(chainWithoutStaking))
        
        // Create new asset with non-staking configuration
        val nonStakingTokenConfig = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(chainId).willReturn("non_staking_chain")
        }
        
        val nonStakingToken = mock(Token::class.java).apply {
            given(configuration).willReturn(nonStakingTokenConfig)
        }
        
        val nonStakingAsset = mock(Asset::class.java).apply {
            given(token).willReturn(nonStakingToken)
        }
        
        givenBlocking { walletRepository.getAssets(metaAccountId) }.willReturn(listOf(nonStakingAsset))
        
        // Create new instance with updated mocks
        val newStakingSharedState = StakingSharedState(
            chainRegistry = chainRegistry,
            preferences = preferences,
            walletRepository = walletRepository,
            accountRepository = accountRepository,
            chainsRepository = chainsRepository,
            scope = testScope
        )
        
        val selections = newStakingSharedState.availableToSelect()
        assertTrue("Expected empty selections but got: ${selections.size} selections", selections.isEmpty())
    }

    @Test
    fun `should properly handle parachain staking type`() = runTest(testDispatcher, timeout = 5.seconds) {
        // Setup chain asset for parachain staking
        val parachainAsset = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(staking).willReturn(StakingType.PARACHAIN)
        }
        
        // Setup chain
        val parachainChain = mock(Chain::class.java).apply {
            given(id).willReturn("parachain")
            given(assets).willReturn(listOf(parachainAsset))
            given(ecosystem).willReturn(Ecosystem.Substrate)
        }

        // Setup meta account
        val testMetaAccount = mock(MetaAccount::class.java).apply {
            given(id).willReturn(metaAccountId)
        }
        given(testMetaAccount.accountId(parachainChain)).willReturn(ByteArray(32))

        val testMetaAccountFlow = MutableStateFlow(testMetaAccount)
        given(accountRepository.selectedMetaAccountFlow()).willReturn(testMetaAccountFlow)

        // Setup chain repository
        givenBlocking { chainsRepository.getChains() }.willReturn(listOf(parachainChain))

        // Setup asset
        val parachainTokenConfig = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(chainId).willReturn("parachain")
        }

        val parachainToken = mock(Token::class.java).apply {
            given(configuration).willReturn(parachainTokenConfig)
        }

        val parachainWalletAsset = mock(Asset::class.java).apply {
            given(token).willReturn(parachainToken)
            given(chainId).willReturn("parachain")
            given(id).willReturn(testAssetId)
        }

        givenBlocking { walletRepository.getAssets(metaAccountId) }.willReturn(listOf(parachainWalletAsset))

        // Setup initial selection
        val initialSelection = StakingAssetSelection.ParachainStaking("parachain", testAssetId)
        val encodedSelection = "${initialSelection.chainId}:${initialSelection.chainAssetId}:${initialSelection.type.name}"
        whenever(preferences.stringFlow(any(), any())).thenReturn(flowOf(encodedSelection))

        // Create new instance
        val newStakingSharedState = StakingSharedState(
            chainRegistry = chainRegistry,
            preferences = preferences,
            walletRepository = walletRepository,
            accountRepository = accountRepository,
            chainsRepository = chainsRepository,
            scope = testScope
        )

        // Wait for initialization
        newStakingSharedState.selectionItem.first()
        newStakingSharedState.assetWithChain.first()
        newStakingSharedState.currentAssetFlow().first()
        
        // Verify selections
        val selections = newStakingSharedState.availableToSelect()
        assertTrue("Expected parachain staking selection but got none", 
            selections.any { it is StakingAssetSelection.ParachainStaking })
        
        val parachainSelection = selections.find { it is StakingAssetSelection.ParachainStaking }
        assertNotNull("Parachain selection should not be null", parachainSelection)
        assertEquals("parachain", parachainSelection?.chainId)
        assertEquals(testAssetId, parachainSelection?.chainAssetId)
    }

    @Test
    fun `should persist selection in preferences`() = runTest(testDispatcher, timeout = 5.seconds) {
        val selection = StakingAssetSelection.Pool(testChainId, testAssetId)
        stakingSharedState.update(selection)
        
        verify(preferences).putString(any(), any())
    }

    @Test
    fun `should properly decode stored selection`() = runTest(testDispatcher, timeout = 5.seconds) {
        // Setup stored selection
        val selection = StakingAssetSelection.Pool(testChainId, testAssetId)
        val encodedSelection = "${selection.chainId}:${selection.chainAssetId}:${selection.type.name}"
        whenever(preferences.stringFlow(any(), any())).thenReturn(flowOf(encodedSelection))

        // Setup chain asset that supports pool staking
        val poolChainAsset = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(staking).willReturn(StakingType.RELAYCHAIN)
        }

        // Setup chain
        val poolChain = mock(Chain::class.java).apply {
            given(id).willReturn(testChainId)
            given(assets).willReturn(listOf(poolChainAsset))
            given(ecosystem).willReturn(Ecosystem.Substrate)
        }

        // Setup meta account
        val testMetaAccount = mock(MetaAccount::class.java).apply {
            given(id).willReturn(metaAccountId)
        }
        given(testMetaAccount.accountId(poolChain)).willReturn(ByteArray(32))

        val testMetaAccountFlow = MutableStateFlow(testMetaAccount)
        given(accountRepository.selectedMetaAccountFlow()).willReturn(testMetaAccountFlow)

        // Setup chain repository
        givenBlocking { chainsRepository.getChains() }.willReturn(listOf(poolChain))

        // Setup asset
        val poolTokenConfig = mock(jp.co.soramitsu.core.models.Asset::class.java).apply {
            given(id).willReturn(testAssetId)
            given(chainId).willReturn(testChainId)
        }

        val poolToken = mock(Token::class.java).apply {
            given(configuration).willReturn(poolTokenConfig)
        }

        val poolAsset = mock(Asset::class.java).apply {
            given(token).willReturn(poolToken)
            given(chainId).willReturn(testChainId)
            given(id).willReturn(testAssetId)
        }

        givenBlocking { walletRepository.getAssets(metaAccountId) }.willReturn(listOf(poolAsset))

        // Create new instance
        val newStakingSharedState = StakingSharedState(
            chainRegistry = chainRegistry,
            preferences = preferences,
            walletRepository = walletRepository,
            accountRepository = accountRepository,
            chainsRepository = chainsRepository,
            scope = testScope
        )

        // Wait for initialization
        newStakingSharedState.selectionItem.first()
        newStakingSharedState.assetWithChain.first()
        newStakingSharedState.currentAssetFlow().first()

        // Verify selection
        val currentSelection = newStakingSharedState.selectionItem.first()
        assertTrue("Expected Pool selection but got ${currentSelection::class.simpleName}", 
            currentSelection is StakingAssetSelection.Pool)
        assertEquals(selection.chainId, currentSelection.chainId)
        assertEquals(selection.chainAssetId, currentSelection.chainAssetId)
    }
} 