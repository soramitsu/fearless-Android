package jp.co.soramitsu.staking.impl.domain.recommendations.settings

import androidx.lifecycle.Lifecycle
import java.util.concurrent.Executors
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The custom validator/collator settings screen asks [RecommendationSettingsProviderFactory.create] for its provider
 * when "Apply" is tapped. With no provider cached yet (the list screen behind it not recreated after the process was
 * restored), create() waited on its own cache entry forever and Apply did nothing. One cache entry also served
 * both staking types, so a relay chain provider could come back where a parachain provider was asked for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationSettingsProviderFactoryTest {

    private lateinit var main: ExecutorCoroutineDispatcher
    private val lifecycle = mock<Lifecycle> { on { currentState } doReturn Lifecycle.State.RESUMED }

    @Before
    fun setUp() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        main.close()
    }

    @Test
    fun `settings screen gets a relay chain provider when none is cached`() = runBlocking {
        val provider = withTimeout(TIMEOUT_MILLIS) { factory().create(lifecycle, Asset.StakingType.RELAYCHAIN) }

        assertTrue(provider.toString(), provider is RecommendationSettingsProvider.RelayChain)
    }

    @Test
    fun `settings screen gets a parachain provider when none is cached`() = runBlocking {
        val provider = withTimeout(TIMEOUT_MILLIS) { factory().create(lifecycle, Asset.StakingType.PARACHAIN) }

        assertTrue(provider.toString(), provider is RecommendationSettingsProvider.Parachain)
    }

    @Test
    fun `a cached relay chain provider is never handed out for a parachain`() = runBlocking {
        val factory = factory()
        val relay = withTimeout(TIMEOUT_MILLIS) { factory.createRelayChain(lifecycle) }

        val parachain: Any = withTimeout(TIMEOUT_MILLIS) { factory.createParachain(lifecycle) }

        assertTrue(parachain.toString(), parachain is RecommendationSettingsProvider.Parachain)
        assertTrue(withTimeout(TIMEOUT_MILLIS) { factory.createRelayChain(lifecycle) } === relay)
    }

    private suspend fun factory(): RecommendationSettingsProviderFactory {
        val constants = mock<StakingConstantsRepository>()
        whenever(constants.maxRewardedNominatorPerValidator(CHAIN)).thenReturn(512)
        whenever(constants.maxValidatorsPerNominator(CHAIN)).thenReturn(16)
        whenever(constants.maxTopDelegationsPerCandidate(CHAIN)).thenReturn(300)
        whenever(constants.maxDelegationsPerDelegator(CHAIN)).thenReturn(100)
        val shared = mock<StakingSharedState>()
        whenever(shared.chainId()).thenReturn(CHAIN)
        return RecommendationSettingsProviderFactory(ComputationalCache(), constants, shared)
    }

    private companion object {
        const val CHAIN = "chain"
        const val TIMEOUT_MILLIS = 10_000L
    }
}
