package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * App-owned portfolio snapshots retain their reviewed registry provenance. Change a snapshot
 * and its digest in the same Android review; no workspace or shared repository is required.
 */
class SharedPortfolioConfigPinTest {

    @Test
    fun `bundled shared portfolio files match their sha256 pins`() {
        val pins = mapOf(
            SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET to CANONICAL_ASSET_GROUPS_SHA256,
            SharedPortfolioConfigSource.TESTNET_CHAIN_IDS_ASSET to TESTNET_CHAIN_IDS_SHA256,
            SharedPortfolioConfigSource.DEFAULT_VISIBLE_AT_ZERO_ASSET to DEFAULT_VISIBLE_AT_ZERO_SHA256
        )

        pins.forEach { (assetName, pin) ->
            assertEquals(
                "$assetName differs from its pin; review the app-owned snapshot and update its digest together",
                pin,
                sha256Hex(bundledAsset(assetName))
            )
        }
    }

    @Test
    fun `every bundled shared file loads and was reviewed against the bundled registry snapshot`() {
        val config = bundledConfig()
        val snapshotSha256 = sha256Hex(testResource(REVIEWED_REGISTRY_RESOURCE))

        assertEquals(snapshotSha256, requireNotNull(config.tokenGroups).registry.snapshotSha256)
        assertEquals(snapshotSha256, requireNotNull(config.testnetChainIds).registry.snapshotSha256)
        assertEquals(snapshotSha256, requireNotNull(config.defaultVisibleAtZero).registry.snapshotSha256)
    }

    private companion object {
        const val CANONICAL_ASSET_GROUPS_SHA256 = "c8875a2e1b8e3a1a0fb4e90962004ce219033a9d5c5dbd49067b5a7ebcfa454e"
        const val TESTNET_CHAIN_IDS_SHA256 = "a3a5433f496e5575c770fdc253277be80fbcf3c97eaa7b37a6b199a2b6e44629"
        const val DEFAULT_VISIBLE_AT_ZERO_SHA256 = "a331788fbcd263ccaf4bcc7d3c83c7430d7c25ac7f9b4e2513606f67b68f20b6"
    }
}
