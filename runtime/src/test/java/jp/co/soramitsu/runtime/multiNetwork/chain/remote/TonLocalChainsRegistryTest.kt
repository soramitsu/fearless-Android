package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TonLocalChainsRegistryTest {

    @Test
    fun `bundled TON mainnet should use shared TI endpoint`() {
        val file = localChainsFile()
        val json = file.readText()
        val chains = JsonParser.parseString(json).asJsonArray
        val mainnet = chains.firstObject { it["chainId"].asString == "-239" }

        assertFalse(json.contains("keeper.tonapi.io"))
        assertEquals(
            UniversalWalletRegistry.TON_INDEXER_BASE_URL,
            mainnet["externalApi"].asJsonObject["history"].asJsonObject["url"].asString
        )
        assertEquals(
            UniversalWalletRegistry.TON_INDEXER_BASE_URL,
            mainnet["nodes"].asJsonArray.first().asJsonObject["url"].asString
        )
    }

    @Test
    fun `bundled TON testnet history should not point at old mainnet tonapi host`() {
        val chains = JsonParser.parseString(localChainsFile().readText()).asJsonArray
        val testnet = chains.firstObject { it["chainId"].asString == "-3" }

        assertEquals(
            UniversalWalletRegistry.TON_INDEXER_BASE_URL,
            testnet["externalApi"].asJsonObject["history"].asJsonObject["url"].asString
        )
    }

    private fun localChainsFile(): File {
        return listOf(
            File("runtime/src/main/assets/local_chains.json"),
            File("src/main/assets/local_chains.json")
        ).first { it.isFile }
    }

    private fun Iterable<com.google.gson.JsonElement>.firstObject(
        predicate: (JsonObject) -> Boolean
    ): JsonObject {
        return first { predicate(it.asJsonObject) }.asJsonObject
    }
}
