package jp.co.soramitsu.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalAssetIdentityTest {

    @Test
    fun `public AssetKey contract contains only ecosystem chain and asset identity`() {
        val key = AssetKey("substrate", "polkadot", "dot-native")

        assertEquals("substrate:polkadot:dot-native", key.serialized)
    }

    @Test
    fun sameSymbolAssetsRemainDistinctByNetworkAndContract() {
        val ethereumUsdc = CanonicalAssetIdentity("ethereum", "ethereum-mainnet", "0xa0b8")
        val polygonUsdc = CanonicalAssetIdentity("ethereum", "polygon", "0x3c49")

        assertNotEquals(ethereumUsdc, polygonUsdc)
        assertNotEquals(ethereumUsdc.serialized, polygonUsdc.serialized)
    }

    @Test
    fun ecosystemIsNormalizedInSerializedIdentity() {
        val identity = CanonicalAssetIdentity("Solana", "SOLANA-MAINNET", "MintCaseSensitive")

        assertEquals("solana:solana-mainnet:MintCaseSensitive", identity.serialized)
    }

    @Test
    fun checksummedAndLowercaseEvmContractsHaveTheSameIdentity() {
        val checksummed = CanonicalAssetIdentity(
            "Ethereum",
            "1",
            "0xA0b86991c6218b36C1d19D4a2e9Eb0cE3606eB48"
        )
        val lowercase = CanonicalAssetIdentity(
            "ethereum",
            "1",
            "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        )

        assertEquals(lowercase, checksummed)
        assertEquals(lowercase.serialized, checksummed.serialized)
    }

    @Test
    fun nonEvmAssetIdentifiersRemainCaseSensitive() {
        assertNotEquals(
            CanonicalAssetIdentity("solana", "mainnet", "MintABC"),
            CanonicalAssetIdentity("solana", "mainnet", "mintabc")
        )
    }
}
