package jp.co.soramitsu.wallet.impl.data.buyToken

import android.content.ContextWrapper
import android.content.Intent
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BuyTokenProvidersCoverageTest {

    private val dot = WalletTestModels.asset(id = "dot", symbol = "dot")

    @Test
    fun `ramp refuses tokens it does not list`() {
        val provider = RampProvider(host = RAMP_HOST, apiToken = "ramp-token")

        assertThrows(BuyTokenRegistry.Provider.UnsupportedTokenException::class.java) {
            provider.createIntegrator(dot.copy(purchaseProviders = listOf("moonpay")), ADDRESS)
        }
    }

    @Test
    fun `ramp opens its widget with the asset, wallet address and return link`() {
        val context = RecordingContext()

        RampProvider(host = RAMP_HOST, apiToken = "ramp-token")
            .createIntegrator(dot.copy(purchaseProviders = listOf("ramp")), ADDRESS)
            .integrate(context)

        val intent = context.started.single()
        val uri = requireNotNull(intent.data)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https", uri.scheme)
        assertEquals(RAMP_HOST, uri.authority)
        assertEquals("DOT", uri.getQueryParameter("swapAsset"))
        assertEquals(ADDRESS, uri.getQueryParameter("userAddress"))
        assertEquals("ramp-token", uri.getQueryParameter("hostApiKey"))
        assertEquals("Fearless Wallet", uri.getQueryParameter("hostAppName"))
        assertTrue(uri.getQueryParameter("hostLogoUrl").orEmpty().endsWith("/ic_wallet.png"))
        assertEquals(ExternalProvider.REDIRECT_URL_BASE, uri.getQueryParameter("finalUrl"))
    }

    @Test
    fun `coinbase opens the onramp with the app id and the asset address template filled in`() {
        val context = RecordingContext()
        val asset = dot.copy(coinbaseUrl = "defaultAsset=DOT&addresses={\"{address}\":[\"polkadot\"]}")

        CoinbaseProvider(host = COINBASE_HOST, appId = "app-1").createIntegrator(asset, ADDRESS).integrate(context)

        val intent = context.started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            "https://$COINBASE_HOST?appId=app-1&defaultAsset=DOT&addresses={\"$ADDRESS\":[\"polkadot\"]}",
            intent.dataString
        )
    }

    @Test
    fun `coinbase without an asset template sends only the app id`() {
        val context = RecordingContext()

        CoinbaseProvider(host = COINBASE_HOST, appId = "app-1").createIntegrator(dot, ADDRESS).integrate(context)

        assertEquals("https://$COINBASE_HOST?appId=app-1&", context.started.single().dataString)
    }

    @Test
    fun `moonpay creates an integrator for the asset it supports`() {
        val provider = MoonPayProvider(host = "buy-sandbox.moonpay.com", publicKey = "pk_test_public")
        val asset = dot.copy(purchaseProviders = listOf("moonpay"))

        assertTrue(provider.isTokenSupported(asset))
        assertTrue(provider.createIntegrator(asset, ADDRESS) is MoonPayProvider.MoonPayIntegrator)
    }

    /** Records the browser intents instead of starting an activity. */
    private class RecordingContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
        }
    }

    private companion object {
        const val RAMP_HOST = "ri-widget-staging.firebaseapp.com"
        const val COINBASE_HOST = "pay.coinbase.com/buy/select-asset"
        const val ADDRESS = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    }
}
