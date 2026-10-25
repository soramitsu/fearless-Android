package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenGroupCatalogTest {

    @Test
    fun `bundled catalog approves dot, ksm, eth, usdt, usdc and btc and only proposes the rest`() {
        val catalog = bundledCatalog()

        assertEquals(
            listOf("dot", "ksm", "eth", "usdt", "usdc", "btc"),
            catalog.groups.filter { it.status == TokenGroupStatus.Approved }.map(TokenGroupDefinition::id)
        )
        assertEquals(listOf("dot", "ksm", "eth", "usdt", "usdc", "btc"), catalog.renderableGroups.map(TokenGroupDefinition::id))
        assertTrue(catalog.groups.none { it.id == "weth-ethereum" && it.status == TokenGroupStatus.Approved })
        assertEquals(listOf(12, 16, 4, 3, 6, 1), catalog.renderableGroups.map { it.members.size })
    }

    @Test
    fun `unknown keys reject the whole catalog`() {
        assertRejected { addProperty("remoteOverrideUrl", "https://example.invalid/groups.json") }
        assertRejected { group("dot").addProperty("priceSource", "group") }
        assertRejected { group("eth").firstMember().addProperty("trusted", true) }
        assertRejected { getAsJsonObject("registry").addProperty("signature", "none") }
    }

    @Test
    fun `missing required keys reject the whole catalog`() {
        assertRejected { remove("registry") }
        assertRejected { group("dot").remove("priceId") }
        assertRejected { group("eth").firstMember().remove("precision") }
    }

    @Test
    fun `unsupported format or version is rejected`() {
        assertRejected { addProperty("format", "fearless.token-groups") }
        assertRejected { addProperty("version", 2) }
        assertRejected { addProperty("version", "1") }
    }

    @Test
    fun `duplicate keys, lenient syntax and trailing content are rejected`() {
        val valid = bundledAsset(SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET).decodeToString()
        val duplicateKey = valid.replaceFirst("\"version\": 1,", "\"version\": 1, \"version\": 1,")
        val comment = valid.replaceFirst("{", "{ // reviewed\n")
        val trailing = "$valid{}"

        listOf(duplicateKey, comment, trailing).forEach { text ->
            assertTrue("fixture must differ from the bundled file", text != valid)
            assertThrows(Exception::class.java) { TokenGroupCatalog.parse(text.toByteArray()) }
        }
        assertThrows(Exception::class.java) { TokenGroupCatalog.parse(byteArrayOf(0xC3.toByte(), 0x28)) }
    }

    @Test
    fun `oversize catalog is rejected`() {
        assertRejected { group("dot").addProperty("review", "x".repeat(SharedPortfolioJson.MAX_FILE_BYTES)) }
    }

    @Test
    fun `group ids, statuses and limits follow the shared schema`() {
        assertRejected { group("dot").addProperty("id", "DOT") }
        assertRejected { group("dot").addProperty("id", "-dot") }
        assertRejected { group("ksm").addProperty("id", "dot") }
        assertRejected { group("dot").addProperty("status", "trusted") }
        assertRejected { group("eth").firstMember().addProperty("precision", -1) }
        assertRejected { group("eth").firstMember().addProperty("precision", 18.5) }
        assertRejected { group("eth").add("members", JsonArray()) }
        assertRejected {
            val members = group("eth").getAsJsonArray("members")
            repeat(TokenGroupCatalog.MAX_MEMBERS) { members.add(members[0].deepCopy()) }
        }
        assertRejected {
            val groups = getAsJsonArray("groups")
            val template = group("aave-ethereum")
            (groups.size()..TokenGroupCatalog.MAX_GROUPS).forEach { index ->
                groups.add(template.deepCopy().apply { addProperty("id", "extra-$index") })
            }
        }
    }

    @Test
    fun `a member listed in two groups invalidates both groups`() {
        val catalog = parseMutated {
            group("eth").getAsJsonArray("members").add(group("dot").firstMember().deepCopy())
        }

        assertEquals(listOf("ksm", "usdt", "usdc", "btc"), catalog.renderableGroups.map(TokenGroupDefinition::id))
    }

    @Test
    fun `a member shared with a proposed group invalidates the approved group too`() {
        val catalog = parseMutated {
            group("usdt-ethereum").getAsJsonArray("members").add(group("usdt").firstMember().deepCopy())
        }

        assertTrue(catalog.renderableGroups.none { it.id == "usdt" })
        assertTrue(catalog.renderableGroups.any { it.id == "usdc" })
    }

    @Test
    fun `a member repeated inside one group invalidates that group`() {
        val catalog = parseMutated {
            group("eth").getAsJsonArray("members").add(group("eth").firstMember().deepCopy())
        }

        assertTrue(catalog.renderableGroups.none { it.id == "eth" })
        assertTrue(catalog.renderableGroups.any { it.id == "dot" })
    }

    @Test
    fun `an evm member spelled with different case still counts as a duplicate`() {
        val catalog = parseMutated {
            val members = group("usdc").getAsJsonArray("members")
            members.add(
                members.map { it.asJsonObject }.single { it["assetId"].asString == ARBITRUM_USDC_ASSET_ID }.deepCopy().apply {
                    addProperty("assetId", "0xaf88d065e77c8cC2239327C5EDb3A432268e5831")
                }
            )
        }

        assertTrue(catalog.renderableGroups.none { it.id == "usdc" })
    }

    @Test
    fun `an approved group whose members declare different precisions never renders`() {
        val catalog = parseMutated { group("eth").firstMember().addProperty("precision", 8) }

        assertTrue(catalog.renderableGroups.none { it.id == "eth" })
        assertEquals(5, catalog.renderableGroups.size)
    }

    @Test
    fun `proposed groups never render`() {
        val catalog = bundledCatalog()
        val proposed = catalog.groups.filter { it.status == TokenGroupStatus.Proposed }

        assertTrue(proposed.size > 30)
        assertTrue(catalog.renderableGroups.none { it.status == TokenGroupStatus.Proposed })
        assertTrue(parseMutated { group("dot").addProperty("status", "proposed") }.renderableGroups.none { it.id == "dot" })
    }

    @Test
    fun `bundled catalog distrusts the third-party Kusama AssetHub usdt`() {
        val untrusted = bundledCatalog().untrustedAssets

        assertEquals(
            listOf(KUSAMA_ASSET_HUB_CHAIN_ID to KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID),
            untrusted.entries.map { it.chainId to it.assetId }
        )
        assertEquals("tether", untrusted.entries.single().registryPriceId)
        assertTrue(untrusted.contains(KUSAMA_ASSET_HUB_CHAIN_ID, KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID))
        assertTrue(
            "no group, approved or proposed, lists an untrusted asset",
            bundledCatalog().groups.none { group ->
                group.members.any { untrusted.contains(it.chainId, it.assetId) }
            }
        )
    }

    @Test
    fun `a group that lists an untrusted asset never renders`() {
        val catalog = parseMutated {
            group("usdt").getAsJsonArray("members").add(
                group("usdt").firstMember().deepCopy().apply {
                    addProperty("chainId", KUSAMA_ASSET_HUB_CHAIN_ID)
                    addProperty("assetId", KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID)
                }
            )
        }

        assertEquals(listOf("dot", "ksm", "eth", "usdc", "btc"), catalog.renderableGroups.map(TokenGroupDefinition::id))
    }

    @Test
    fun `an untrusted asset is matched whatever the case of its id`() {
        val untrusted = bundledCatalog().untrustedAssets

        assertTrue(untrusted.contains(KUSAMA_ASSET_HUB_CHAIN_ID, KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID.uppercase()))
        assertTrue(!untrusted.contains(POLKADOT_ASSET_HUB_CHAIN_ID, KUSAMA_ASSET_HUB_THIRD_PARTY_USDT_ASSET_ID))
        assertTrue(!untrusted.contains(KUSAMA_ASSET_HUB_CHAIN_ID, DOT_ASSET_ID))
    }

    @Test
    fun `a catalog without the untrusted list is valid and lists nothing`() {
        val catalog = parseMutated { remove("untrustedAssets") }

        assertTrue(catalog.untrustedAssets.entries.isEmpty())
        assertEquals(6, catalog.renderableGroups.size)
    }

    @Test
    fun `untrusted assets in a shape this build does not know reject the whole catalog`() {
        assertRejected { addProperty("untrustedAssets", "none") }
        assertRejected { firstUntrusted().addProperty("trustedUntil", "2027-01-01") }
        assertRejected { firstUntrusted().remove("registryPriceId") }
        assertRejected { firstUntrusted().remove("reason") }
        assertRejected { firstUntrusted().addProperty("precision", -1) }
        assertRejected { firstUntrusted().addProperty("reviewedAt", "yesterday") }
        assertRejected { getAsJsonArray("untrustedAssets").add(firstUntrusted().deepCopy()) }
    }

    private fun JsonObject.firstUntrusted(): JsonObject = getAsJsonArray("untrustedAssets")[0].asJsonObject

    private fun parseMutated(mutate: JsonObject.() -> Unit): TokenGroupCatalog {
        return TokenGroupCatalog.parse(bundledCatalogJson().apply(mutate).toBytes())
    }

    private fun assertRejected(mutate: JsonObject.() -> Unit) {
        val bytes = bundledCatalogJson().apply(mutate).toBytes()
        assertThrows(Exception::class.java) { TokenGroupCatalog.parse(bytes) }
    }

    private fun JsonObject.firstMember(): JsonObject = getAsJsonArray("members")[0].asJsonObject
}
