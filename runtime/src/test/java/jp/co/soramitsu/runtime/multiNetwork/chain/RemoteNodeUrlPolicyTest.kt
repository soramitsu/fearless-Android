package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.JsonParser
import jp.co.soramitsu.core.models.Ecosystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteNodeUrlPolicyTest {
    private val release = RemoteNodeUrlPolicy.Release

    @Test
    fun `release builds take only encrypted Substrate nodes`() {
        assertTrue(release.admits("wss://rpc.polkadot.io", Ecosystem.Substrate))
        assertTrue(release.admits("WSS://rpc.polkadot.io/ws", Ecosystem.EthereumBased))
        for (url in listOf("ws://rpc.polkadot.io", "https://rpc.polkadot.io", "http://rpc.polkadot.io", "rpc.polkadot.io", "wss://", "", null)) {
            assertFalse("$url", release.admits(url, Ecosystem.Substrate))
        }
    }

    @Test
    fun `release builds take HTTPS for EVM and TON APIs but never plaintext`() {
        assertTrue(release.admits("https://ethereum.publicnode.com", Ecosystem.Ethereum))
        assertTrue(release.admits("wss://ethereum.publicnode.com", Ecosystem.Ethereum))
        assertTrue(release.admits("https://tonapi.io", Ecosystem.Ton))
        assertFalse(release.admits("http://ethereum.publicnode.com", Ecosystem.Ethereum))
        assertFalse(release.admits("ws://tonapi.io", Ecosystem.Ton))
    }

    @Test
    fun `debug builds keep local development nodes`() {
        assertTrue(RemoteNodeUrlPolicy.Debug.admits("ws://127.0.0.1:9944", Ecosystem.Substrate))
        assertTrue(RemoteNodeUrlPolicy.forBuild(isDebug = true).admits("http://10.0.2.2:8545", Ecosystem.Ethereum))
        assertFalse(RemoteNodeUrlPolicy.forBuild(isDebug = false).admits("ws://127.0.0.1:9944", Ecosystem.Substrate))
    }

    @Test
    fun `every node the production catalog lists today passes the release policy`() {
        for (path in listOf("../remote-config/production/chains/v13/chains.json")) {
            val chains = JsonParser.parseString(File(path).readText()).asJsonArray.map { it.asJsonObject }
            val refused = chains.filter { !it.has("disabled") || !it.get("disabled").asBoolean }.flatMap { chain ->
                val ecosystem = Ecosystem.fromString(chain.get("ecosystem")?.asString ?: "substrate")
                chain.getAsJsonArray("nodes")?.map { it.asJsonObject.get("url").asString }.orEmpty()
                    .filter { !release.admits(it, ecosystem) }
                    .map { "${chain.get("name").asString}: $it" }
            }
            assertEquals("$path lists nodes a release build refuses", emptyList<String>(), refused)
        }
    }
}
