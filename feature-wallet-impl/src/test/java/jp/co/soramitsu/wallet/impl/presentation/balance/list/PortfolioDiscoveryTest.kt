package jp.co.soramitsu.wallet.impl.presentation.balance.list

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioDiscoveryTest {

    @Test fun `search matches token symbol, token name and network name, ignoring case and spaces`() {
        val relay = asset("relay", "Relay Network", symbol = "dot", name = "Polkadot", total = "2", fiat = "10")
        assertTrue(PortfolioDiscovery.matches(relay, " DOT "))
        assertTrue(PortfolioDiscovery.matches(relay, "polka"))
        assertTrue(PortfolioDiscovery.matches(relay, "relay net"))
        assertTrue(PortfolioDiscovery.matches(relay, ""))
        assertTrue(PortfolioDiscovery.matches(relay, "   "))
        assertFalse(PortfolioDiscovery.matches(relay, "Ethereum"))
        assertFalse(PortfolioDiscovery.matches(relay, "dot polkadot"))
    }

    @Test fun `chips list funded verified networks, largest trusted value first`() {
        val relay = asset("relay", "Relay", total = "2", fiat = "10")
        val hub = asset("hub", "Asset Hub", total = "3", fiat = "5")
        val unpriced = asset("unpriced", "Unpriced", total = "1", fiat = null)
        val zero = asset("zero", "Zero", total = "0", fiat = null)
        val unknown = asset("unknown", "Unknown", total = "0", fiat = null).copy(total = null)
        val hidden = asset("hidden", "Hidden", total = "2", fiat = "9999", hidden = true)
        val detected = asset("detected", "Detected", total = "5", fiat = "999999", trust = "unverified")
        val testnet = asset("testnet", "Testnet", total = "5", fiat = "999999", testnet = true)

        val options = PortfolioDiscovery.networks(listOf(hub, relay, relay, hidden, zero, unknown, unpriced, detected, testnet), null)

        assertEquals(listOf("relay", "hub", "testnet", "unpriced"), options.map { it.id })
        assertEquals(PortfolioNetworkOption("relay", "Relay"), options.first())
    }

    @Test fun `an untrusted price adds nothing to a network's place`() {
        val untrusted = asset("untrusted", "Untrusted", total = "1", fiat = "1000", priceTrust = "untrusted")
        val trusted = asset("trusted", "Trusted", total = "1", fiat = "1")
        assertEquals(listOf("trusted", "untrusted"), PortfolioDiscovery.networks(listOf(untrusted, trusted), null).map { it.id })
    }

    @Test fun `equal values rank by funded asset count, then by name`() {
        val a = asset("a", "Beta", total = "1", fiat = null)
        val b1 = asset("b", "Gamma", total = "1", fiat = null)
        val b2 = asset("b", "Gamma", id = "2", total = "1", fiat = null)
        val c = asset("c", "alpha", total = "1", fiat = null)
        assertEquals(listOf("b", "c", "a"), PortfolioDiscovery.networks(listOf(a, b1, b2, c), null).map { it.id })
    }

    @Test fun `a selected network keeps its chip, first, when it holds nothing`() {
        val relay = asset("relay", "Relay", total = "2", fiat = "10")
        assertEquals(listOf("zero", "relay"), PortfolioDiscovery.networks(listOf(relay), PortfolioNetworkOption("zero", "Zero")).map { it.id })
        assertEquals(listOf("relay"), PortfolioDiscovery.networks(listOf(relay), PortfolioNetworkOption("relay", "Relay")).map { it.id })
        assertEquals(listOf("zero"), PortfolioDiscovery.networks(emptyList(), PortfolioNetworkOption("zero", "Zero")).map { it.id })
    }

    @Test fun `picking the selected chip or All networks shows every network`() {
        assertNull(PortfolioDiscovery.toggleNetwork("relay", "relay"))
        assertNull(PortfolioDiscovery.toggleNetwork("relay", null))
        assertNull(PortfolioDiscovery.toggleNetwork(null, null))
        assertEquals("hub", PortfolioDiscovery.toggleNetwork("relay", "hub"))
        assertEquals("hub", PortfolioDiscovery.toggleNetwork(null, "hub"))
    }

    @Suppress("LongParameterList")
    private fun asset(
        chain: String,
        chainName: String,
        id: String = "0",
        symbol: String = "DOT",
        name: String = "Polkadot",
        total: String,
        fiat: String?,
        hidden: Boolean = false,
        trust: String = "verified",
        testnet: Boolean = false,
        priceTrust: String = if (fiat != null) "canonical" else "untrusted"
    ) = PortfolioAsset(
        AssetListItemViewState(index = 0, assetIconUrl = "", assetName = name, assetChainName = chainName, assetSymbol = symbol,
            assetTokenFiat = null, assetTokenRate = null, assetTransferableBalance = total, assetTransferableBalanceFiat = fiat,
            assetChainUrls = emptyMap(), chainId = chain, chainAssetId = id, isSupported = true, isHidden = hidden,
            isTestnet = testnet, metadataTrust = trust, priceTrust = priceTrust),
        BigDecimal(total), fiat?.let(::BigDecimal), "$", null
    )
}
