package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Byte-for-byte parity with the merkleized-metadata 0.5.1 crate on the real V15 metadata of every catalog chain that
 * lists `CheckMetadataHash`: the leaves, the tree root, the extrinsic metadata, the digest encoding and its hash.
 */
class MetadataDigestTest {

    private val chains = MetadataHashFixtures.vectors.chains

    @Test
    fun `fixtures cover every required chain and only chains that check the hash`() {
        val names = chains.map { it.name }.toSet()
        listOf("Polkadot", "Kusama", "Westend", "Polkadot AssetHub", "Kusama AssetHub").forEach { required ->
            assertTrue("missing $required", required in names)
        }
        chains.forEach { chain ->
            assertTrue(chain.name, ChainMetadataHash.EXTENSION in chain.signedExtensions)
            val bytes = MetadataHashFixtures.metadataBytes(chain)
            assertEquals(chain.name, chain.metadataSha256, MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(false))
        }
    }

    @Test
    fun `type leaves match the crate`() {
        chains.forEach { chain ->
            val information = TypeInformation.of(MetadataHashFixtures.metadata(chain))
            val leafHashes = information.types.map { it.hash() }
            assertEquals(chain.name, chain.leafCount, leafHashes.size)
            assertEquals(chain.name, chain.firstLeafHash, leafHashes.first().toHexString(true))
            assertEquals(chain.name, chain.lastLeafHash, leafHashes.last().toHexString(true))
            val joined = MessageDigest.getInstance("SHA-256").digest(leafHashes.reduce(ByteArray::plus))
            assertEquals(chain.name, chain.leafHashesSha256, joined.toHexString(false))
        }
    }

    @Test
    fun `tree root and extrinsic metadata match the crate`() {
        chains.forEach { chain ->
            val information = TypeInformation.of(MetadataHashFixtures.metadata(chain))
            assertEquals(chain.name, chain.typesTreeRoot, MetadataMerkleTree.root(information.types).toHexString(true))
            assertEquals(chain.name, chain.extrinsicMetadataEncoded, information.extrinsicMetadata.encode().toHexString(true))
            assertEquals(chain.name, chain.extrinsicMetadataHash, information.extrinsicMetadata.hash().toHexString(true))
            assertEquals(chain.name, chain.signedExtensions, information.extrinsicMetadata.signedExtensions.map { it.identifier })
        }
    }

    @Test
    fun `metadata hash with the chain's own extra information matches the crate`() {
        chains.forEach { chain ->
            val metadata = MetadataHashFixtures.metadata(chain)
            val digest = MetadataDigest.generate(metadata, ChainMetadataHash.extraInfo(metadata, chain.decimals, chain.tokenSymbol))
            assertEquals(chain.name, chain.digestEncoded, digest.encode().toHexString(true))
            assertEquals(chain.name, chain.metadataHash, digest.hash().toHexString(true))
            assertEquals(chain.name, chain.metadataHash, ChainMetadataHash.compute(metadata, chain.decimals, chain.tokenSymbol).toHexString(true))
        }
    }

    @Test
    fun `metadata hash with the crate's test extra information matches the crate`() {
        val extraInfo = ExtraInfo(specVersion = 1, specName = "nice", base58Prefix = 1, decimals = 1, tokenSymbol = "lol")
        chains.forEach { chain ->
            val digest = MetadataDigest.generate(MetadataHashFixtures.metadata(chain), extraInfo)
            assertEquals(chain.name, chain.syntheticMetadataHash, digest.hash().toHexString(true))
        }
    }

    @Test
    fun `every extra information field changes the hash`() {
        val chain = MetadataHashFixtures.chain("Westend")
        val metadata = MetadataHashFixtures.metadata(chain)
        val base = ChainMetadataHash.extraInfo(metadata, chain.decimals, chain.tokenSymbol)
        val variants = listOf(
            ExtraInfo(base.specVersion + 1, base.specName, base.base58Prefix, base.decimals, base.tokenSymbol),
            ExtraInfo(base.specVersion, base.specName + "x", base.base58Prefix, base.decimals, base.tokenSymbol),
            ExtraInfo(base.specVersion, base.specName, base.base58Prefix + 1, base.decimals, base.tokenSymbol),
            ExtraInfo(base.specVersion, base.specName, base.base58Prefix, base.decimals + 1, base.tokenSymbol),
            ExtraInfo(base.specVersion, base.specName, base.base58Prefix, base.decimals, base.tokenSymbol + "x")
        )
        val expected = chain.metadataHash
        variants.forEach { info ->
            assertTrue(MetadataDigest.generate(metadata, info).hash().toHexString(true) != expected)
        }
    }

    @Test
    fun `digest encoding is the V1 enum variant with little-endian integers`() {
        val digest = MetadataDigest(ByteArray(32) { 1 }, ByteArray(32) { 2 }, ExtraInfo(0x01020304, "ab", 0x0506, 7, "c"))
        val expected = byteArrayOf(1) + ByteArray(32) { 1 } + ByteArray(32) { 2 } +
            byteArrayOf(4, 3, 2, 1) + byteArrayOf(8, 'a'.code.toByte(), 'b'.code.toByte()) + byteArrayOf(6, 5) + byteArrayOf(7) +
            byteArrayOf(4, 'c'.code.toByte())
        assertArrayEquals(expected, digest.encode())
    }
}
