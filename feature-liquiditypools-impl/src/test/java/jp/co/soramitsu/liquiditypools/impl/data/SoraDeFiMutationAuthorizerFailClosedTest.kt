package jp.co.soramitsu.liquiditypools.impl.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.coreAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

/** Every SORA mutation precondition fails closed with its own reason (real SORA runtime metadata). */
class SoraDeFiMutationAuthorizerFailClosedTest {

    private val node = FakeSoraNode(listOf(xor, valToken))
    private val meta = mockk<MetaAccount>(relaxed = true) {
        every { id } returns 7L
        every { substrateAccountId } returns ACCOUNT
    }
    private val accounts = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns meta
        coEvery { isSigningRecoveryRequired(7L, any<IChain>()) } returns false
        coEvery { hasSigningMaterial(7L, any<IChain>()) } returns true
    }
    private val wallet = mockk<WalletRepository> {
        coEvery { getAccountSpendableBalance(any(), any()) } returns BigInteger.valueOf(100)
    }
    private val toggles = mockk<ProductFeatureToggleStore> {
        every { polkaswapMutationsEnabled } returns true
        every { demeterMutationsEnabled } returns true
    }
    private val authorizer = SoraDeFiMutationAuthorizer(node.chainRegistry, accounts, wallet, toggles)

    @Test
    fun `a supported runtime call and account are a capability`() = runBlocking {
        assertNull(reason(SoraMutationFeature.Demeter, setOf(SoraRuntimeCall("DemeterFarmingPlatform", "get_rewards"))))
        assertEquals(
            "SORA runtime call DemeterFarmingPlatform.missing is unavailable",
            reason(SoraMutationFeature.Demeter, setOf(SoraRuntimeCall("DemeterFarmingPlatform", "missing")))
        )
        every { toggles.demeterMutationsEnabled } returns false
        assertEquals("Demeter actions are temporarily unavailable", reason(SoraMutationFeature.Demeter))
    }

    @Test
    fun `network, runtime and account preconditions fail closed`() = runBlocking {
        assertEquals("This action requires the exact SORA main network", failure(chainId = "other"))

        every { node.chain.id } returns "other"
        assertEquals("Resolved SORA chain identity does not match the request", failure())
        every { node.chain.id } returns soraMainChainId

        coEvery { node.chainRegistry.getRuntimeOrNull(soraMainChainId) } returns null
        assertEquals("SORA runtime is unavailable", failure())
        coEvery { node.chainRegistry.getRuntimeOrNull(soraMainChainId) } returns SoraRuntime.runtime

        coEvery { accounts.isSigningRecoveryRequired(7L, any<IChain>()) } returns true
        assertEquals("Recover this wallet's signing material before continuing", failure())
        coEvery { accounts.isSigningRecoveryRequired(7L, any<IChain>()) } returns false

        every { meta.substrateAccountId } returns null
        assertEquals("Add a SORA account before continuing", failure())
    }

    @Test
    fun `requested assets must be exact registered SORA currencies`() = runBlocking {
        assertEquals(
            "Requested asset belongs to a different network",
            failure(assets = listOf(CanonicalSoraAsset(valToken).copy(chainId = "other")))
        )
        assertEquals(
            "Requested asset has no exact SORA currency id",
            failure(assets = listOf(CanonicalSoraAsset(valToken).copy(currencyId = " ")))
        )
        assertEquals(
            "SORA currency id is not uniquely registered",
            failure(assets = listOf(CanonicalSoraAsset(coreAsset("dai", "DAI", "0x0dai"))))
        )
    }

    @Test
    fun `an unexplained failure still reports an unavailable context`() = runBlocking {
        coEvery { node.chainRegistry.getChain(soraMainChainId) } throws IllegalStateException()

        assertEquals("SORA mutation context is unavailable", reason(SoraMutationFeature.Liquidity))
    }

    @Test
    fun `fresh balances need a non-negative fee, positive inputs and enough XOR`() = runBlocking {
        val context = authorizer.authorize(SoraMutationFeature.Liquidity, soraMainChainId, listOf(CanonicalSoraAsset(valToken)), emptySet())

        assertEquals("XOR fee estimate must not be negative", balanceFailure(context, emptyList(), BigInteger.valueOf(-1)))
        assertEquals(
            "Input amount must be at least one plank",
            balanceFailure(context, listOf(SoraAssetSpend(valToken, BigInteger.ZERO, "VAL short")), BigInteger.ONE)
        )
        assertEquals("XOR balance is insufficient for the network fee", balanceFailure(context, emptyList(), BigInteger.valueOf(101)))
        assertNull(balanceFailure(context, listOf(SoraAssetSpend(valToken, BigInteger.TEN, "VAL short")), BigInteger.TEN))
    }

    private suspend fun reason(
        feature: SoraMutationFeature,
        calls: Set<SoraRuntimeCall> = emptySet()
    ) = authorizer.capabilityReason(feature, soraMainChainId, calls)

    private suspend fun failure(
        chainId: String = soraMainChainId,
        assets: List<CanonicalSoraAsset> = emptyList()
    ) = runCatching { authorizer.authorize(SoraMutationFeature.Liquidity, chainId, assets, emptySet()) }.exceptionOrNull()?.message

    private suspend fun balanceFailure(context: SoraMutationContext, spends: List<SoraAssetSpend>, fee: BigInteger) =
        runCatching { authorizer.requireFreshBalances(context, spends, fee) }.exceptionOrNull()?.message

    private companion object {
        val ACCOUNT = ByteArray(32) { 7 }
    }
}
