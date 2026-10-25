package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class SoraStakingRewardsCurrencyTest {
    private val config = mock<Asset>()
    private val chain = mock<Chain> {
        on { assetsById } doReturn mapOf(SoraStakingRewardsScenario.REWARD_ASSET_ID to config)
    }
    private val registry = mock<ChainRegistry> { onBlocking { getChain(any()) } doReturn chain }
    private val tokens = mock<TokenRepository>()
    private val rpcRegistry = mock<IChainRegistry>()
    private val scenario = SoraStakingRewardsScenario(RpcCalls(rpcRegistry), registry, tokens)

    @Test fun rewardUsesTheCurrentCatalogConfigurationAndCurrencyBoundRepositoryToken() = runTest {
        val first = mock<Token>()
        val second = mock<Token>()
        whenever(tokens.getToken(config)).thenReturn(first, second)
        assertSame(first, scenario.getRewardAsset())
        assertSame(second, scenario.getRewardAsset())
        verify(tokens, times(2)).getToken(same(config))
        verifyNoInteractions(rpcRegistry)
    }

    @Test fun rewardLookupFailureIsNotReplacedWithAnUnverifiedPrice() = runTest {
        val failure = IllegalStateException("quote unavailable")
        whenever(tokens.getToken(config)).thenThrow(failure)
        val actual = runCatching { scenario.getRewardAsset() }.exceptionOrNull()
        assertSame(failure, actual)
    }

    @Test fun cancelledRewardLookupPropagates() = runTest {
        val cancellation = CancellationException("selection changed")
        whenever(tokens.getToken(config)).thenThrow(cancellation)
        assertSame(cancellation, runCatching { scenario.getRewardAsset() }.exceptionOrNull())
    }

    @Test fun cancelledConversionQuoteCannotBecomeZero() = runTest {
        val cancellation = CancellationException("quote cancelled")
        whenever(rpcRegistry.getConnection(any())).thenThrow(cancellation)
        assertSame(cancellation, runCatching { scenario.mainAssetToRewardAssetRate() }.exceptionOrNull())
    }
}
