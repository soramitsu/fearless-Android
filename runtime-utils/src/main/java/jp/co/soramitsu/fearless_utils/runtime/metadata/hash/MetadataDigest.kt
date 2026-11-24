// Ported from merkleized-metadata 0.5.1, src/lib.rs, src/types.rs (MetadataDigest) and src/merkle_tree.rs
// (MerkleTree::new and root) (https://github.com/bkchr/merkleized-metadata).
// Copyright (c) Bastian Köcher. Licensed MIT OR Apache-2.0; used here under the Apache License 2.0.

package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

/** `ExtraInfo`: what the metadata hash binds besides the types. It must be what the runtime was built with. */
class ExtraInfo(
    val specVersion: Long,
    val specName: String,
    val base58Prefix: Int,
    val decimals: Int,
    val tokenSymbol: String
)

/** `MetadataDigest::V1`. Its [hash] is the RFC 78 metadata hash that `CheckMetadataHash` signs. */
class MetadataDigest(
    val typesTreeRoot: ByteArray,
    val extrinsicMetadataHash: ByteArray,
    val extraInfo: ExtraInfo
) {
    fun encode(): ByteArray = ScaleOutput()
        .writeU8(DIGEST_V1)
        .writeBytes(typesTreeRoot)
        .writeBytes(extrinsicMetadataHash)
        .writeU32(extraInfo.specVersion)
        .writeString(extraInfo.specName)
        .writeU16(extraInfo.base58Prefix)
        .writeU8(extraInfo.decimals)
        .writeString(extraInfo.tokenSymbol)
        .toByteArray()

    fun hash(): ByteArray = Blake3.hash256(encode())

    companion object {
        private const val DIGEST_V1 = 1
        private const val U8_MAX = 0xff
        private const val U16_MAX = 0xffff
        private const val U32_MAX = 0xffff_ffffL

        /** `generate_metadata_digest`. */
        fun generate(metadata: FrameMetadataV15, extraInfo: ExtraInfo): MetadataDigest {
            require(extraInfo.specVersion in 0..U32_MAX) { "spec_version is a u32" }
            require(extraInfo.base58Prefix in 0..U16_MAX) { "base58_prefix is a u16" }
            require(extraInfo.decimals in 0..U8_MAX) { "decimals is a u8" }
            val information = TypeInformation.of(metadata)
            return MetadataDigest(
                typesTreeRoot = MetadataMerkleTree.root(information.types),
                extrinsicMetadataHash = information.extrinsicMetadata.hash(),
                extraInfo = extraInfo
            )
        }
    }
}

/** The root of `MerkleTree::new` over the type leaves. Proof generation is not needed for signing and not ported. */
object MetadataMerkleTree {
    fun root(leaves: List<MerkleizedType>): ByteArray = rootOfLeafHashes(leaves.map { it.hash() })

    /**
     * The tree is built from the back: the last two nodes are joined, and their parent goes to the front, until one
     * node is left. A node hashes the concatenation of its left and right child; no leaves give 32 zero bytes.
     */
    fun rootOfLeafHashes(leafHashes: List<ByteArray>): ByteArray {
        val nodes = ArrayDeque(leafHashes)
        while (nodes.size > 1) {
            val right = nodes.removeLast()
            val left = nodes.removeLast()
            nodes.addFirst(Blake3.hash256(left + right))
        }
        return nodes.removeLastOrNull() ?: ByteArray(Blake3.HASH_SIZE)
    }
}
