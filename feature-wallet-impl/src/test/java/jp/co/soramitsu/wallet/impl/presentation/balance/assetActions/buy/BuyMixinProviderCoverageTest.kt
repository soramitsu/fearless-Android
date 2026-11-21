package jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.mockk
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Buy opens the only provider that sells the token directly, or lets the user choose among several. */
class BuyMixinProviderCoverageTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val ramp = FakeProvider("Ramp")
    private val moonpay = FakeProvider("Moonpay")
    private val singleSeller = WalletTestModels.asset(id = "dot").copy(purchaseProviders = listOf("ramp"))
    private val twoSellers = WalletTestModels.asset(id = "ksm", chainId = "kusama").copy(purchaseProviders = listOf("ramp", "moonpay"))
    private val noSeller = WalletTestModels.asset(id = "wnd", chainId = "westend")
    private val chainRegistry = mockk<ChainRegistry> {
        coEvery { getAsset("polkadot", "dot") } returns singleSeller
        coEvery { getAsset("kusama", "ksm") } returns twoSellers
        coEvery { getAsset("westend", "wnd") } returns noSeller
        coEvery { getAsset("missing", "x") } returns null
    }
    private val mixin = BuyMixinProvider(BuyTokenRegistry(listOf(ramp, moonpay)), chainRegistry)

    @Test
    fun buyIsEnabledOnlyForAKnownAssetThatAProviderSells(): Unit = runBlocking {
        assertTrue(mixin.isBuyEnabled("polkadot", "dot"))
        assertTrue(mixin.isBuyEnabled("kusama", "ksm"))
        assertFalse(mixin.isBuyEnabled("westend", "wnd"))
        assertFalse(mixin.isBuyEnabled("missing", "x"))
    }

    @Test
    fun aSingleProviderOpensDirectly(): Unit = runBlocking {
        mixin.buyClicked("polkadot", "dot", ADDRESS)

        val payload = mixin.integrateWithBuyProviderEvent.value!!.peekContent()
        assertSame(ramp, payload.provider)
        assertEquals(singleSeller, payload.chainAsset)
        assertEquals(ADDRESS, payload.address)
        assertNull(mixin.showProviderChooserEvent.value)
    }

    @Test
    fun severalProvidersAreOfferedForAChoice(): Unit = runBlocking {
        mixin.buyClicked("kusama", "ksm", ADDRESS)

        val payload = mixin.showProviderChooserEvent.value!!.peekContent()
        assertEquals(listOf(ramp, moonpay), payload.providers)
        assertEquals(twoSellers, payload.chainAsset)
        assertEquals(ADDRESS, payload.accountAddress)
        assertNull(mixin.integrateWithBuyProviderEvent.value)

        mixin.providerChosen(moonpay, twoSellers, ADDRESS)
        assertSame(moonpay, mixin.integrateWithBuyProviderEvent.value!!.peekContent().provider)
    }

    @Test
    fun buyingAnUnknownOrUnsoldAssetIsRefused() {
        val unknown = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { mixin.buyClicked("missing", "x", ADDRESS) }
        }
        assertEquals("No asset found with id = x for chain missing", unknown.message)

        val unsold = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { mixin.buyClicked("westend", "wnd", ADDRESS) }
        }
        assertEquals("No provider found for WND", unsold.message)
    }

    private class FakeProvider(override val name: String) : BuyTokenRegistry.Provider<BuyTokenRegistry.Integrator<Unit>> {
        override val icon: Int = 0

        override fun createIntegrator(chainAsset: Asset, address: String): BuyTokenRegistry.Integrator<Unit> =
            error("Integration is not part of this test")
    }

    private companion object {
        const val ADDRESS = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    }
}
