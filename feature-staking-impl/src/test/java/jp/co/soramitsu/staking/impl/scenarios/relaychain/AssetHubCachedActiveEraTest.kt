package jp.co.soramitsu.staking.impl.scenarios.relaychain

import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigInteger

/**
 * The staking ledger updater reads the active era that ActiveEraUpdater caches in the same update system, never with
 * a remote read that could fail and end every updater. On an Asset Hub an empty era is still an error, not era 0.
 */
class AssetHubCachedActiveEraTest {
    private val local = mock<StorageDataSource>()
    private val remote = mock<StorageDataSource>()
    private val repository = StakingRelayChainScenarioRepository(remote, local, mock(), mock(), mock(), mock())

    private suspend fun cache(scale: String?) {
        whenever(local.query<BigInteger>(any(), any(), anyOrNull(), any())).thenAnswer { invocation ->
            invocation.getArgument<Binder<BigInteger>>(3).invoke(scale, mock<RuntimeSnapshot>())
        }
    }

    @Test
    fun `a Hub's cached era is read without a remote read`() = runTest {
        whenever(local.query<BigInteger>(any(), any(), anyOrNull(), any())).thenReturn(7.toBigInteger())
        assertEquals(7.toBigInteger(), repository.getCachedActiveEraIndex(AssetHubStaking.POLKADOT))
        verifyNoInteractions(remote)
    }

    @Test
    fun `an empty Hub era is an error`() = runTest {
        cache(null)
        val failure = runCatching { repository.getCachedActiveEraIndex(AssetHubStaking.KUSAMA) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        verifyNoInteractions(remote)
    }

    @Test
    fun `other networks keep the lenient era`() = runTest {
        cache(null)
        assertEquals(BigInteger.ZERO, repository.getCachedActiveEraIndex("validator-contract"))
        verifyNoInteractions(remote)
    }

    private companion object {
        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }
    }
}
