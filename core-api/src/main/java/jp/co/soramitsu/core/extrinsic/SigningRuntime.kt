package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion

/** The signed extension through which a runtime checks the RFC 78 metadata hash a transaction was signed with. */
const val CHECK_METADATA_HASH = "CheckMetadataHash"

/**
 * The runtime a transaction is built and signed with, and how the signature commits to that runtime's metadata.
 * Only a runtime that checks the metadata hash can tell a node's false metadata from the real one: with the hash, a
 * transaction built from other metadata than the chain's is invalid instead of meaning another call.
 */
sealed class SigningRuntime(val runtime: RuntimeSnapshot) {

    /** The runtime does not list `CheckMetadataHash`; nothing about its metadata can be signed. */
    class WithoutMetadataCheck(runtime: RuntimeSnapshot) : SigningRuntime(runtime)

    /** The runtime checks the metadata hash, and [metadataHash] is the hash of exactly [runtime]'s metadata. */
    class WithMetadataHash(runtime: RuntimeSnapshot, metadataHash: ByteArray) : SigningRuntime(runtime) {
        private val hash = metadataHash.copyOf()

        val metadataHash: ByteArray
            get() = hash.copyOf()

        init {
            require(hash.size == METADATA_HASH_SIZE) { "A metadata hash is 32 bytes" }
        }
    }

    /**
     * The runtime lists `CheckMetadataHash` but was reviewed to have been built without a hash, so it refuses every
     * transaction signed with one. Only a reviewed network policy may say so; see `MetadataHashExemptions`.
     */
    class MetadataHashExempt(runtime: RuntimeSnapshot, val reason: String) : SigningRuntime(runtime)

    /** The hash the signature commits to, or null for Mode::Disabled; refuses an inconsistent choice. */
    fun metadataHashToSign(): ByteArray? {
        val checksHash = CHECK_METADATA_HASH in runtime.metadata.extrinsic.signedExtensions
        return when (this) {
            is WithoutMetadataCheck -> {
                if (checksHash) throw MetadataHashUnavailableException("The network checks the metadata hash, but none was computed")
                null
            }
            is WithMetadataHash -> {
                require(checksHash) { "A metadata hash was computed for a runtime that does not check it" }
                metadataHash
            }
            is MetadataHashExempt -> null
        }
    }

    private companion object {
        const val METADATA_HASH_SIZE = 32
    }
}

/**
 * Where signing paths get their runtime. For a runtime that checks the metadata hash it returns the runtime built from
 * the V15 metadata the hash is computed from, or fails: nothing is then signed, and nothing falls back to Mode::Disabled.
 */
fun interface SigningRuntimeSource {
    /**
     * @param runtimeVersion the runtime version the transaction is signed for (`CheckSpecVersion`, `CheckTxVersion`).
     * @throws MetadataHashUnavailableException when the runtime checks the metadata hash and it cannot be computed.
     */
    suspend fun signingRuntime(chain: IChain, runtimeVersion: RuntimeVersion): SigningRuntime
}

/** The network checks the metadata hash and it could not be computed for the current runtime, so nothing was signed. */
class MetadataHashUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
