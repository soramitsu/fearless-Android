package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.WalletTestModels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeWalletConstantsCoverageTest {

    private val chainRegistry = mockk<ChainRegistry>()
    private val constants = RuntimeWalletConstants(chainRegistry)

    @Test
    fun `existential deposit is the Balances constant of the runtime`(): Unit = runBlocking {
        coEvery { chainRegistry.getRuntimeOrNull("polkadot") } returns POLKADOT

        val deposit = constants.existentialDeposit(WalletTestModels.asset(existentialDeposit = "1"))

        assertEquals(BigInteger.valueOf(10_000_000_000), deposit)
    }

    @Test
    fun `existential deposit falls back to the registry value only when the runtime has no constant`(): Unit = runBlocking {
        coEvery { chainRegistry.getRuntimeOrNull("polkadot") } returns EMPTY
        coEvery { chainRegistry.getRuntimeOrNull("kusama") } returns null

        assertEquals(BigInteger.valueOf(123), constants.existentialDeposit(WalletTestModels.asset(existentialDeposit = "123")))
        assertNull(constants.existentialDeposit(WalletTestModels.asset(existentialDeposit = null)))
        assertNull(constants.existentialDeposit(WalletTestModels.asset(chainId = "kusama", existentialDeposit = "123")))
    }

    @Test
    fun `tip comes from the runtime overrides when Balances has no DefaultTip constant`(): Unit = runBlocking {
        val overridden = RuntimeSnapshot(POLKADOT.typeRegistry, POLKADOT.metadata, mapOf("Balances" to mapOf("DefaultTip" to "5")))
        coEvery { chainRegistry.getRuntimeOrNull("polkadot") } returns POLKADOT
        coEvery { chainRegistry.getRuntimeOrNull("overridden") } returns overridden
        coEvery { chainRegistry.getRuntimeOrNull("offline") } returns null

        assertNull(constants.tip("polkadot"))
        assertEquals(BigInteger.valueOf(5), constants.tip("overridden"))
        assertNull(constants.tip("offline"))
    }

    private companion object {
        val EMPTY = RuntimeSnapshot(
            mockk<TypeRegistry>(),
            RuntimeMetadata(BigInteger.valueOf(14), emptyMap(), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
        )

        val POLKADOT: RuntimeSnapshot by lazy {
            val loader = requireNotNull(RuntimeWalletConstantsCoverageTest::class.java.classLoader)
            val metadata = GZIPInputStream(requireNotNull(loader.getResourceAsStream("parity/polkadot_metadata_v14.hex.gz")))
                .bufferedReader().use { it.readText() }.trim()
            val types = requireNotNull(loader.getResourceAsStream("parity/polkadot_parity_types.json"))
                .bufferedReader().use { it.readText() }
            RuntimeFactory(JsonFactory.create()).constructRuntime(metadata, types, 2005000).runtime
        }
    }
}
