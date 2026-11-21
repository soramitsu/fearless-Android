package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.getResourceReader
import java.util.zip.GZIPInputStream

/**
 * Real V15 metadata of every catalog chain whose runtime lists `CheckMetadataHash` (2026-11-07), with the digests the
 * merkleized-metadata 0.5.1 crate computes for them. See `provenance` in `metadata-hash/vectors.json`.
 */
object MetadataHashFixtures {

    class Vectors(val provenance: String, val chains: List<Chain>)

    class Chain(
        val name: String,
        val chainId: String,
        val metadata: String,
        val metadataSha256: String,
        val block: String,
        val node: String,
        val specName: String,
        val specVersion: Long,
        val transactionVersion: Long,
        val base58Prefix: Int,
        val decimals: Int,
        val tokenSymbol: String,
        val signedExtensions: List<String>,
        val leafCount: Int,
        val leafHashesSha256: String,
        val firstLeafHash: String,
        val lastLeafHash: String,
        val typesTreeRoot: String,
        val extrinsicMetadataHash: String,
        val extrinsicMetadataEncoded: String,
        val digestEncoded: String,
        val metadataHash: String,
        val syntheticMetadataHash: String
    )

    val vectors: Vectors by lazy {
        Gson().fromJson(getResourceReader("metadata-hash/vectors.json"), Vectors::class.java)
    }

    fun chain(name: String): Chain = vectors.chains.single { it.name == name }

    private val cache = HashMap<String, ByteArray>()

    /** The raw `RuntimeMetadataPrefixed` bytes. */
    @Synchronized
    fun metadataBytes(chain: Chain): ByteArray = cache.getOrPut(chain.metadata) {
        val stream = requireNotNull(javaClass.classLoader!!.getResourceAsStream(chain.metadata)) { chain.metadata }
        GZIPInputStream(stream).use { it.readBytes() }
    }

    fun metadata(chain: Chain): FrameMetadataV15 = FrameMetadataV15.decode(metadataBytes(chain))
}
