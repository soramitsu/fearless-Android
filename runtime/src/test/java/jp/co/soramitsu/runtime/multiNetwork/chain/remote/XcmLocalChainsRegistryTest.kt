package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XcmLocalChainsRegistryTest {

    @Test
    fun `bundled XCM registry carries no execution authority`() {
        // Every former route spec was retired: relay reserve transfers of DOT and KSM are rejected on chain since
        // the Asset Hub Migration, and the engine could not encode the specs anyway. Authority returns only with a
        // per-destination reserve decision, a dry run against the live runtimes and funded evidence.
        val destinations = destinations(chains())
        val routeAssets = destinations.flatMap { destination ->
            assertTrue("legacy destination-scoped execution must be absent", !destination.has("execution"))
            destination["assets"]?.asJsonArray?.map { it.asJsonObject }.orEmpty()
        }

        assertEquals(0, routeAssets.count { it.has("execution") })
    }

    @Test
    fun `bundled XCM registry advertises no route to or from a chain that no longer exists`() {
        val chains = chains()

        chains.filter { it["chainId"].asString in retiredChainIds }.forEach { retired ->
            assertTrue("${retired["name"].asString} must not carry an xcm block", !retired.has("xcm"))
        }
        destinations(chains).forEach { destination ->
            val destinationId = destination["chainId"].asString
            assertTrue("route to retired chain $destinationId", destinationId !in retiredChainIds)
        }
    }

    @Test
    fun `bundled XCM registry keeps its discovery inventory`() {
        val destinations = destinations(chains())
        val routeAssets = destinations.flatMap { it["assets"]?.asJsonArray?.map { asset -> asset.asJsonObject }.orEmpty() }

        assertEquals(27, destinations.size)
        assertEquals(39, routeAssets.size)
    }

    private fun chains(): List<JsonObject> =
        JsonParser.parseString(localChainsFile().readText()).asJsonArray.map { it.asJsonObject }

    private fun destinations(chains: List<JsonObject>): List<JsonObject> = chains.flatMap { chain ->
        chain["xcm"]?.asJsonObject
            ?.get("availableDestinations")?.asJsonArray
            ?.map { it.asJsonObject }
            .orEmpty()
    }

    private fun localChainsFile(): File {
        return listOf(
            File("runtime/src/main/assets/local_chains.json"),
            File("src/main/assets/local_chains.json")
        ).first { it.isFile }
    }

    private companion object {
        // Genesis hashes of Moonbeam, Moonriver and Parallel. None of their parachain ids is registered on its relay
        // chain any more (Moonbeam and Moonriver were sunset in July 2026, Parallel stopped operating).
        val retiredChainIds = setOf(
            "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d",
            "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b",
            "e61a41c53f5dcd0beb09df93b34402aada44cb05117b71059cce40a2723a4e97"
        )
    }
}
