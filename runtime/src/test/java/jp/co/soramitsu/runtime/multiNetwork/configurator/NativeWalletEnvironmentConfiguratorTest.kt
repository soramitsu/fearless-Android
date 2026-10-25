package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.nativeWalletChains
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class NativeWalletEnvironmentConfiguratorTest {
    @Test
    fun `native HTTP networks never create Substrate or EVM connections`() = runBlocking {
        val connection = mock(ConnectionPool::class.java)
        val runtime = mock(RuntimeProviderPool::class.java)
        val sync = mock(RuntimeSyncService::class.java)
        val subscriptions = mock(RuntimeSubscriptionPool::class.java)
        val chains = mock(ChainsRepository::class.java)
        val ethereum = mock(EthereumConnectionPool::class.java)
        val provider = ChainEnvironmentConfiguratorProvider(connection, runtime, sync, subscriptions, ethereum)

        nativeWalletChains(setOf("bitcoin:mainnet", "solana:mainnet")).forEach { chain ->
            val configurator = provider.provide(chain)
            assertTrue(configurator is NativeWalletEnvironmentConfigurator)
            configurator.configure(chain)
        }
        verifyNoInteractions(connection, runtime, sync, subscriptions, ethereum)
    }
}
