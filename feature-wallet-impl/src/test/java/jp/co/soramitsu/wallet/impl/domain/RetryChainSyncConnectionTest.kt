package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.mockk
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.RegistryConnectionFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The network issue banner's "Retry" sets a network up again. The registry owns that connection: a network
 * background sync does not keep is disconnected once the retry has its answer, instead of staying connected
 * until the process ends; a network it keeps stays connected.
 */
class RetryChainSyncConnectionTest {

    private fun interactor(fixture: RegistryConnectionFixture) = WalletInteractorImpl(
        walletRepository = mockk(),
        addressBookRepository = mockk(),
        accountRepository = mockk(),
        historyRepository = mockk(),
        chainRegistry = fixture.registry,
        fileProvider = mockk(),
        preferences = mockk(),
        selectedFiat = mockk(),
        xcmEntitiesFetcher = mockk(),
        chainsRepository = mockk<ChainsRepository> {
            coEvery { getChain("a") } returns fixture.rankedChain
            coEvery { getChain("b") } returns fixture.unrankedChain
        },
        networkStateService = mockk(),
        tokenRepository = mockk(),
        coroutineContext = Dispatchers.Unconfined
    )

    @Test
    fun `retry of a network outside background sync disconnects it once the retry has its answer`() = runBlocking {
        RegistryConnectionFixture().use { f ->
            val result = interactor(f).retryChainSync("b")

            assertTrue(result.isSuccess)
            assertEquals(listOf("a", "b"), f.configured)
            assertEquals(listOf("b"), f.disconnected)
        }
    }

    @Test
    fun `retry of a network background sync keeps sets it up again and leaves it connected`() = runBlocking {
        RegistryConnectionFixture().use { f ->
            val result = interactor(f).retryChainSync("a")

            assertTrue(result.isSuccess)
            assertEquals(listOf("a", "a"), f.configured)
            assertEquals(emptyList<String>(), f.disconnected)
        }
    }
}
