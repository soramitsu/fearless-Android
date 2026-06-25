package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XcmLocalChainsRegistryTest {

    @Test
    fun `bundled XCM registry contains executable relay native routes`() {
        val chains = JsonParser.parseString(localChainsFile().readText()).asJsonArray

        executableRelayRoutes.forEach { route ->
            val origin = chains.firstObject { it["chainId"].asString == route.originChainId }
            val destination = origin["xcm"]
                .asJsonObject["availableDestinations"]
                .asJsonArray
                .firstObject { it["chainId"].asString == route.destinationChainId }

            assertTrue(
                "${route.assetSymbol} route ${route.originChainId} -> ${route.destinationChainId} is missing asset metadata",
                destination["assets"].asJsonArray.any { it.asJsonObject["symbol"].asString == route.assetSymbol }
            )

            val execution = destination["execution"].asJsonObject
            assertEquals("PolkadotXcm", execution["palletName"].asString)
            assertEquals("limitedReserveTransferAssets", execution["callName"].asString)
            assertEquals("limitedReserveTransferAssets", execution["transferType"].asString)
            assertEquals(route.destinationParents, execution.location("destinationLocation")["parents"].asInt)
            assertEquals(route.destinationInterior, execution.location("destinationLocation")["interior"].asString)
            assertEquals(route.assetParents, execution.location("assetLocation")["parents"].asInt)
            assertEquals(route.assetInterior, execution.location("assetLocation")["interior"].asString)
            assertEquals(route.feeAssetParents, execution.location("feeAssetLocation")["parents"].asInt)
            assertEquals(route.feeAssetInterior, execution.location("feeAssetLocation")["interior"].asString)
            assertEquals(route.beneficiaryParents, execution.location("beneficiaryLocation")["parents"].asInt)
            assertEquals(route.beneficiaryInterior, execution.location("beneficiaryLocation")["interior"].asString)
            assertTrue(execution.location("beneficiaryLocation")["interior"].asString.contains("<account>"))
            assertEquals("Unlimited", execution["weightLimit"].asJsonObject["type"].asString)
            assertEquals("Included", execution["destinationFee"].asJsonObject["mode"].asString)
            assertEquals(route.assetSymbol, execution["destinationFee"].asJsonObject["assetSymbol"].asString)
        }
    }

    private fun JsonObject.location(name: String): JsonObject = get(name).asJsonObject

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

    private companion object {
        const val POLKADOT_CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val POLKADOT_ASSET_HUB_CHAIN_ID = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val MOONBEAM_CHAIN_ID = "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d"
        const val ACALA_CHAIN_ID = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"
        const val PARALLEL_CHAIN_ID = "e61a41c53f5dcd0beb09df93b34402aada44cb05117b71059cce40a2723a4e97"
        const val KUSAMA_CHAIN_ID = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        const val KUSAMA_ASSET_HUB_CHAIN_ID = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"
        const val MOONRIVER_CHAIN_ID = "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b"
        const val KARURA_CHAIN_ID = "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b"
        const val BIFROST_CHAIN_ID = "9f28c6a68e0fc9646eff64935684f6eeeece527e37bbe1f213d22caa1d9d6bed"
        const val ACCOUNT_ID32_BENEFICIARY = "X1(AccountId32({network: Any, id: <account>}))"
        const val ACCOUNT_KEY20_BENEFICIARY = "X1(AccountKey20({network: Any, key: <account>}))"

        val executableRelayRoutes = listOf(
            relayToParachainRoute(POLKADOT_CHAIN_ID, POLKADOT_ASSET_HUB_CHAIN_ID, "DOT", "1000", ACCOUNT_ID32_BENEFICIARY),
            relayToParachainRoute(POLKADOT_CHAIN_ID, MOONBEAM_CHAIN_ID, "DOT", "2004", ACCOUNT_KEY20_BENEFICIARY),
            relayToParachainRoute(POLKADOT_CHAIN_ID, ACALA_CHAIN_ID, "DOT", "2000", ACCOUNT_ID32_BENEFICIARY),
            relayToParachainRoute(POLKADOT_CHAIN_ID, PARALLEL_CHAIN_ID, "DOT", "2012", ACCOUNT_ID32_BENEFICIARY),
            relayToParachainRoute(KUSAMA_CHAIN_ID, KUSAMA_ASSET_HUB_CHAIN_ID, "KSM", "1000", ACCOUNT_ID32_BENEFICIARY),
            relayToParachainRoute(KUSAMA_CHAIN_ID, MOONRIVER_CHAIN_ID, "KSM", "2023", ACCOUNT_KEY20_BENEFICIARY),
            relayToParachainRoute(KUSAMA_CHAIN_ID, KARURA_CHAIN_ID, "KSM", "2000", ACCOUNT_ID32_BENEFICIARY),
            parachainToRelayRoute(POLKADOT_ASSET_HUB_CHAIN_ID, POLKADOT_CHAIN_ID, "DOT"),
            parachainToRelayRoute(ACALA_CHAIN_ID, POLKADOT_CHAIN_ID, "DOT"),
            parachainToRelayRoute(PARALLEL_CHAIN_ID, POLKADOT_CHAIN_ID, "DOT"),
            parachainToRelayRoute(MOONBEAM_CHAIN_ID, POLKADOT_CHAIN_ID, "DOT"),
            parachainToRelayRoute(KUSAMA_ASSET_HUB_CHAIN_ID, KUSAMA_CHAIN_ID, "KSM"),
            parachainToRelayRoute(KARURA_CHAIN_ID, KUSAMA_CHAIN_ID, "KSM"),
            parachainToRelayRoute(MOONRIVER_CHAIN_ID, KUSAMA_CHAIN_ID, "KSM"),
            parachainToRelayRoute(BIFROST_CHAIN_ID, KUSAMA_CHAIN_ID, "KSM")
        )

        fun relayToParachainRoute(
            originChainId: String,
            destinationChainId: String,
            assetSymbol: String,
            destinationParaId: String,
            beneficiaryInterior: String
        ) = ExecutableRelayRoute(
            originChainId = originChainId,
            destinationChainId = destinationChainId,
            assetSymbol = assetSymbol,
            destinationParents = 0,
            destinationInterior = "X1(Parachain($destinationParaId))",
            assetParents = 0,
            assetInterior = "Here",
            beneficiaryParents = 0,
            beneficiaryInterior = beneficiaryInterior,
            feeAssetParents = 0,
            feeAssetInterior = "Here"
        )

        fun parachainToRelayRoute(
            originChainId: String,
            destinationChainId: String,
            assetSymbol: String
        ) = ExecutableRelayRoute(
            originChainId = originChainId,
            destinationChainId = destinationChainId,
            assetSymbol = assetSymbol,
            destinationParents = 1,
            destinationInterior = "Here",
            assetParents = 1,
            assetInterior = "Here",
            beneficiaryParents = 0,
            beneficiaryInterior = ACCOUNT_ID32_BENEFICIARY,
            feeAssetParents = 1,
            feeAssetInterior = "Here"
        )
    }

    private data class ExecutableRelayRoute(
        val originChainId: String,
        val destinationChainId: String,
        val assetSymbol: String,
        val destinationParents: Int,
        val destinationInterior: String,
        val assetParents: Int,
        val assetInterior: String,
        val beneficiaryParents: Int,
        val beneficiaryInterior: String,
        val feeAssetParents: Int,
        val feeAssetInterior: String
    )
}
