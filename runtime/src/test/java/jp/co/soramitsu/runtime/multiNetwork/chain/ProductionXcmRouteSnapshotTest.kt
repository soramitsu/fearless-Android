package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The production chain catalog is served from this repository's master branch, so a merged edit reaches every
 * install within minutes. Its XCM route membership must equal the reviewed snapshot: adding or removing a route
 * fails here unless `reviewed-production-xcm-routes.tsv` changes in the same commit, and CODEOWNERS sends both
 * files to the security team.
 */
class ProductionXcmRouteSnapshotTest {

    @Test
    fun `production XCM route membership equals the reviewed snapshot`() {
        val catalog = routesOf(JsonParser.parseString(File(CATALOG).readText()).asJsonArray.map { it.asJsonObject })
        val reviewed = reviewedRoutes()

        assertEquals("routes listed twice in $CATALOG", catalog.size, catalog.toSet().size)
        assertEquals(
            "$CATALOG changed its XCM routes; review them and update $SNAPSHOT in the same commit.\n" +
                "Added: ${catalog.toSet() - reviewed}\nRemoved: ${reviewed - catalog.toSet()}",
            reviewed,
            catalog.toSet()
        )
    }

    @Test
    fun `the snapshot lists every route once`() {
        val lines = snapshotLines()
        assertEquals(lines.size, lines.toSet().size)
        assertTrue(lines.all { it.split('\t').size == FIELDS })
    }

    // origin chain id, destination chain id, asset symbol, asset id: one line per xcm.availableDestinations asset.
    private fun routesOf(chains: List<JsonObject>): List<String> = chains.flatMap { chain ->
        val origin = chain.get("chainId").asString
        val destinations = chain.getAsJsonObject("xcm")?.getAsJsonArray("availableDestinations")?.map { it.asJsonObject }.orEmpty()
        destinations.flatMap { destination ->
            val destinationId = destination.get("chainId").asString
            destination.getAsJsonArray("assets")?.map { it.asJsonObject }.orEmpty().map { asset ->
                listOf(origin, destinationId, asset.get("symbol").asString, asset.get("id").asString).joinToString("\t")
            }
        }
    }

    private fun reviewedRoutes(): Set<String> = snapshotLines().toSet()

    private fun snapshotLines(): List<String> = File(SNAPSHOT).readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }

    private companion object {
        const val CATALOG = "../remote-config/production/chains/v13/chains.json"
        const val SNAPSHOT = "src/test/resources/reviewed-production-xcm-routes.tsv"
        const val FIELDS = 4
    }
}
