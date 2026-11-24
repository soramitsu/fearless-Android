package jp.co.soramitsu.wallet.impl.domain.implementations

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.calls.getExistentialDeposit
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUnavailableException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class ExistentialDepositUseCaseImplTest {
    private val chain = mockk<Chain> {
        every { isEthereumChain } returns false
        every { ecosystem } returns Ecosystem.Substrate
    }
    private val registry = mockk<ChainRegistry> {
        coEvery { getRuntimeOrNull(any()) } returns null
        coEvery { getChain(any()) } returns chain
    }
    private val rpcCalls = mockk<RpcCalls>()
    private val useCase = ExistentialDepositUseCaseImpl(registry, rpcCalls, mockk())

    @Before
    fun mockRpcExtensions() {
        mockkStatic(RPC_EXTENSIONS)
    }

    @After
    fun restoreRpcExtensions() {
        unmockkStatic(RPC_EXTENSIONS)
    }

    @Test
    fun configuredMinimumIsUsedWithoutAnyLookup(): Unit = runBlocking {
        val result = useCase(asset(ChainAssetType.Normal, existentialDeposit = "10000000000"))

        assertEquals(BigInteger("10000000000"), result.getOrThrow())
        coVerify(exactly = 0) { registry.getRuntimeOrNull(any()) }
    }

    @Test
    fun evmAndTonAssetsHaveNoMinimumWithoutARuntime(): Unit = runBlocking {
        for (type in listOf(ChainAssetType.ERC20, ChainAssetType.BEP20, ChainAssetType.Jetton)) {
            assertEquals(BigInteger.ZERO, useCase(asset(type)).getOrThrow())
        }

        every { chain.isEthereumChain } returns true
        assertEquals(BigInteger.ZERO, useCase(asset(ChainAssetType.Normal)).getOrThrow())

        every { chain.isEthereumChain } returns false
        every { chain.ecosystem } returns Ecosystem.Ton
        assertEquals(BigInteger.ZERO, useCase(asset(ChainAssetType.Normal)).getOrThrow())
    }

    @Test
    fun aSubstrateAssetWithoutARuntimeHasAnUnknownMinimum(): Unit = runBlocking {
        val asset = asset(ChainAssetType.Normal)

        val failure = useCase(asset).exceptionOrNull()

        assertTrue(failure is ExistentialDepositUnavailableException)
        assertSame(asset, (failure as ExistentialDepositUnavailableException).chainAsset)
        assertEquals("Existential deposit of KAR on Karura is unavailable", failure.message)
        assertEquals("Runtime of karura is not loaded", failure.cause?.message)
    }

    @Test
    fun equilibriumIgnoresTheConfiguredMinimum(): Unit = runBlocking {
        val failure = useCase(asset(ChainAssetType.Equilibrium, existentialDeposit = "5")).exceptionOrNull()

        assertTrue(failure is ExistentialDepositUnavailableException)
    }

    @Test
    fun tokenAssetsAskTheNodeWithTheirCurrencyId(): Unit = runBlocking {
        coEvery { registry.getRuntimeOrNull("karura") } returns mockk<RuntimeSnapshot>()
        coEvery { rpcCalls.getExistentialDeposit(any(), any()) } returns BigInteger("100")

        assertEquals(BigInteger("100"), useCase(asset(ChainAssetType.OrmlAsset, symbol = "kusd")).getOrThrow())
        assertEquals(BigInteger("100"), useCase(asset(ChainAssetType.ForeignAsset, currencyId = "3")).getOrThrow())
        assertEquals(BigInteger("100"), useCase(asset(ChainAssetType.Token2, currencyId = "4")).getOrThrow())
        assertEquals(BigInteger("100"), useCase(asset(ChainAssetType.LiquidCrowdloan, currencyId = "13")).getOrThrow())
        assertEquals(BigInteger("100"), useCase(asset(ChainAssetType.StableAssetPoolToken, currencyId = "0")).getOrThrow())

        coVerify { rpcCalls.getExistentialDeposit("karura", "token" to "KUSD") }
        coVerify { rpcCalls.getExistentialDeposit("karura", "foreignAsset" to BigInteger("3")) }
        coVerify { rpcCalls.getExistentialDeposit("karura", "token2" to BigInteger("4")) }
        coVerify { rpcCalls.getExistentialDeposit("karura", "liquidCrowdloan" to BigInteger("13")) }
        coVerify { rpcCalls.getExistentialDeposit("karura", "stableAssetPoolToken" to BigInteger("0")) }
    }

    @Test
    fun assetsThatCannotNameTheirCurrencyOrSourceFail(): Unit = runBlocking {
        coEvery { registry.getRuntimeOrNull("karura") } returns mockk<RuntimeSnapshot>()

        val noCurrency = useCase(asset(ChainAssetType.Token2, currencyId = null)).exceptionOrNull()
        assertEquals("KAR on Karura has no currency id", noCurrency?.cause?.message)

        val noAssetId = useCase(asset(ChainAssetType.Assets, currencyId = null)).exceptionOrNull()
        assertEquals("KAR on Karura has no asset id", noAssetId?.cause?.message)

        for (type in listOf(ChainAssetType.AssetId, ChainAssetType.Xcm)) {
            val failure = useCase(asset(type, currencyId = "1")).exceptionOrNull()
            assertEquals("No existential deposit source for $type assets", failure?.cause?.message)
        }
        assertEquals(BigInteger.ZERO, useCase(asset(ChainAssetType.ERC20)).getOrThrow())
        coVerify(exactly = 0) { rpcCalls.getExistentialDeposit(any(), any()) }
    }

    @Test
    fun aFailedNodeLookupIsReportedAsUnavailable(): Unit = runBlocking {
        coEvery { registry.getRuntimeOrNull("karura") } returns mockk<RuntimeSnapshot>()
        coEvery { rpcCalls.getExistentialDeposit(any(), any()) } throws IllegalStateException("node offline")

        val failure = useCase(asset(ChainAssetType.Stable, symbol = "aUSD")).exceptionOrNull()

        assertTrue(failure is ExistentialDepositUnavailableException)
        assertEquals("node offline", failure?.cause?.message)
        coVerify { rpcCalls.getExistentialDeposit("karura", "token" to "AUSD") }
    }

    private fun asset(
        type: ChainAssetType,
        existentialDeposit: String? = null,
        currencyId: String? = null,
        symbol: String = "KAR"
    ) = CoreAsset(
        id = "asset-$type",
        name = null,
        symbol = symbol,
        iconUrl = "",
        chainId = "karura",
        chainName = "Karura",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 12,
        staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = false,
        type = type,
        currencyId = currencyId,
        existentialDeposit = existentialDeposit,
        color = null,
        isNative = null
    )

    private companion object {
        const val RPC_EXTENSIONS = "jp.co.soramitsu.core.rpc.calls.RpcCallExtensionsKt"
    }
}
