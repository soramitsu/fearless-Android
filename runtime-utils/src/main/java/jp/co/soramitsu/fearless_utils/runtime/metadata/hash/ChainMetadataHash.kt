package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

/** The runtime identity stated by the System pallet's `Version` constant, which the runtime's own `Core_version` returns. */
class MetadataRuntimeVersion(val specName: String, val specVersion: Long, val transactionVersion: Long)

/**
 * The metadata hash a runtime built with `substrate-wasm-builder`'s `enable_metadata_hash` compiles in, computed the
 * same way: from its V15 metadata, the spec name and version of that runtime, the System pallet's `SS58Prefix` and the
 * token decimals and symbol the chain states in `system_properties`.
 */
object ChainMetadataHash {
    const val EXTENSION = "CheckMetadataHash"

    // substrate-wasm-builder (metadata_hash.rs, extract_ss58_prefix) falls back to 42 without the constant.
    private const val DEFAULT_SS58_PREFIX = 42
    private const val SYSTEM = "System"

    /** Whether the runtime checks a metadata hash at all. */
    fun advertises(metadata: FrameMetadataV15): Boolean = metadata.extrinsic.signedExtensions.any { it.identifier == EXTENSION }

    fun ss58Prefix(metadata: FrameMetadataV15): Int {
        val system = metadata.pallets.firstOrNull { it.name == SYSTEM } ?: throw MetadataDecodeException("The runtime has no System pallet")
        val value = system.constants.firstOrNull { it.name == "SS58Prefix" }?.value ?: return DEFAULT_SS58_PREFIX
        // u16::decode reads the first two bytes.
        return ScaleInput(value).readU16()
    }

    fun runtimeVersion(metadata: FrameMetadataV15): MetadataRuntimeVersion {
        val value = metadata.constant(SYSTEM, "Version") ?: throw MetadataDecodeException("The runtime states no version")
        val input = ScaleInput(value)
        val specName = input.readString()
        input.readString() // impl_name
        input.readU32() // authoring_version
        val specVersion = input.readU32()
        input.readU32() // impl_version
        input.readVec { readBytes(API_ID_SIZE); readU32() } // apis
        val transactionVersion = input.readU32()
        return MetadataRuntimeVersion(specName, specVersion, transactionVersion)
    }

    private const val API_ID_SIZE = 8

    fun extraInfo(metadata: FrameMetadataV15, decimals: Int, tokenSymbol: String): ExtraInfo {
        val version = runtimeVersion(metadata)
        return ExtraInfo(
            specVersion = version.specVersion,
            specName = version.specName,
            base58Prefix = ss58Prefix(metadata),
            decimals = decimals,
            tokenSymbol = tokenSymbol
        )
    }

    fun compute(metadata: FrameMetadataV15, decimals: Int, tokenSymbol: String): ByteArray =
        MetadataDigest.generate(metadata, extraInfo(metadata, decimals, tokenSymbol)).hash()
}
