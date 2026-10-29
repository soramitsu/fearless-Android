package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaRpcEndpoints
import jp.co.soramitsu.common.data.network.solana.SolanaRpcException
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SolanaRpcEndpointsTest {
    @Test
    fun `mainnet asks the build provider then the selected node then the public nodes then other nodes`() {
        assertEquals(listOf(MAINNET_BETA, PUBLICNODE, VIBESTATION), SolanaRpcEndpoints.ordered(MAINNET, MAINNET_BETA))
        assertEquals(listOf(PROVIDER, CUSTOM, MAINNET_BETA, PUBLICNODE, VIBESTATION, OTHER),
            SolanaRpcEndpoints.ordered(MAINNET, CUSTOM, listOf(MAINNET_BETA, OTHER), PROVIDER))
        // The selected public node keeps its place and is never asked twice.
        assertEquals(listOf(PROVIDER, PUBLICNODE, MAINNET_BETA, VIBESTATION),
            SolanaRpcEndpoints.ordered(MAINNET, PUBLICNODE, listOf(MAINNET_BETA), PROVIDER))
        // Only the rate-limited public node has its requests spaced.
        assertEquals(700L, SolanaRpcEndpoints.requestSpacingMillis(VIBESTATION))
        for (url in listOf(MAINNET_BETA, PUBLICNODE, PROVIDER, CUSTOM, LOCAL)) assertEquals(0L, SolanaRpcEndpoints.requestSpacingMillis(url))
    }

    @Test
    fun `pacing and non-stickiness follow the rate-limited host however its URL is written`() {
        for (url in listOf(VIBESTATION, "$VIBESTATION/", "HTTPS://PUBLIC.RPC.SOLANAVIBESTATION.COM", " $VIBESTATION ",
            "https://Public.Rpc.SolanaVibeStation.com/?api-key=public-fixture", "$VIBESTATION.", "$VIBESTATION./", "$VIBESTATION.:443/")) {
            assertEquals(url, 700L, SolanaRpcEndpoints.requestSpacingMillis(url))
            assertEquals(url, "public.rpc.solanavibestation.com", SolanaRpcEndpoints.rateLimitedHost(url))
        }
        for (url in listOf("https://public.rpc.solanavibestation.com.example.invalid", "https://rpc.solanavibestation.com", "not a url", PUBLICNODE,
            "https://xpublic.rpc.solanavibestation.com", "https://public.rpc.solanavibestation.co", "https://public.rpc.solanavibestation.com..")) {
            assertEquals(url, 0L, SolanaRpcEndpoints.requestSpacingMillis(url))
            assertNull(url, SolanaRpcEndpoints.rateLimitedHost(url))
        }
    }

    @Test
    fun `a host is named the same however it is spelled`() {
        for (url in listOf(PUBLICNODE, "$PUBLICNODE/", "https://Solana-RPC.PublicNode.com", "$PUBLICNODE.", "$PUBLICNODE./path?key=x", " $PUBLICNODE ")) {
            assertEquals(url, "solana-rpc.publicnode.com", SolanaRpcEndpoints.hostOf(url))
        }
        assertNull(SolanaRpcEndpoints.hostOf("not a url"))
        assertNull(SolanaRpcEndpoints.hostOf("https://"))
    }

    @Test
    fun `an unusable build provider is ignored and never reaches another cluster`() {
        for (provider in listOf(null, "", "  ", "http://provider.invalid/rpc", "https://provider.invalid/#fragment", "ftp://provider.invalid")) {
            assertEquals(listOf(MAINNET_BETA, PUBLICNODE, VIBESTATION), SolanaRpcEndpoints.ordered(MAINNET, MAINNET_BETA, buildProvider = provider))
        }
        assertNull(SolanaRpcEndpoints.providerOrNull("https://user@provider.invalid"))
        assertEquals(PROVIDER, SolanaRpcEndpoints.providerOrNull(" $PROVIDER "))
        assertEquals(listOf(DEVNET_RPC, LOCAL), SolanaRpcEndpoints.ordered(DEVNET, DEVNET_RPC, listOf(LOCAL), PROVIDER))
        assertEquals(listOf(LOCAL), SolanaRpcEndpoints.ordered(LOCAL_GENESIS, LOCAL, buildProvider = PROVIDER))
    }

    @Test
    fun `invalid other nodes are skipped while an invalid selection and the endpoint cap are enforced`() {
        assertEquals(listOf(CUSTOM, MAINNET_BETA, PUBLICNODE, VIBESTATION),
            SolanaRpcEndpoints.ordered(MAINNET, CUSTOM, listOf("https://bad.invalid/#x", "not a url")))
        assertThrows(SolanaRpcException::class.java) { SolanaRpcEndpoints.ordered(MAINNET, "https://selected.invalid/#fragment") }
        val many = (1..20).map { "https://node$it.invalid" }
        val ordered = SolanaRpcEndpoints.ordered(MAINNET, CUSTOM, many, PROVIDER)
        assertEquals(SolanaRpcEndpoints.MAX_ENDPOINTS, ordered.size)
        assertEquals(listOf(PROVIDER, CUSTOM, MAINNET_BETA, PUBLICNODE, VIBESTATION), ordered.take(5))
    }

    private companion object {
        const val MAINNET = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
        const val DEVNET = UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH
        const val LOCAL_GENESIS = "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3zQawwpjk2NsNY"
        const val MAINNET_BETA = UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL
        const val PUBLICNODE = SolanaRpcEndpoints.PUBLICNODE_MAINNET_RPC_URL
        const val VIBESTATION = SolanaRpcEndpoints.SOLANAVIBESTATION_MAINNET_RPC_URL
        const val DEVNET_RPC = UniversalWalletRegistry.SOLANA_DEVNET_RPC_URL
        const val PROVIDER = "https://provider.invalid/v1/public-fixture-key"
        const val CUSTOM = "https://custom.invalid/rpc?key=public-fixture"
        const val OTHER = "https://other.invalid"
        const val LOCAL = "http://127.0.0.1:8899"
    }
}
