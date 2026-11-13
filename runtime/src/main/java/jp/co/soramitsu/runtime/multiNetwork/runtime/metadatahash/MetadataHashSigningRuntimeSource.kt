package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import jp.co.soramitsu.core.extrinsic.CHECK_METADATA_HASH
import jp.co.soramitsu.core.extrinsic.MetadataHashUnavailableException
import jp.co.soramitsu.core.extrinsic.SigningRuntime
import jp.co.soramitsu.core.extrinsic.SigningRuntimeSource
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.hash.ChainMetadataHash
import jp.co.soramitsu.fearless_utils.runtime.metadata.hash.FrameMetadataV15
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Signing runtimes for networks that check the RFC 78 metadata hash.
 *
 * For such a network the transaction is built from the runtime of the V15 metadata the hash is computed from, never
 * from the V14 metadata the rest of the app reads: whatever the node serves, the signed call and the signed hash then
 * describe the same metadata, and the chain refuses the transaction unless that metadata is its own. When the hash
 * cannot be computed, signing fails with [MetadataHashUnavailableException]; it never falls back to Mode::Disabled.
 * Only [MetadataHashExemptions] lists networks that sign Mode::Disabled although they list the extension.
 */
class MetadataHashSigningRuntimeSource(
    private val currentRuntime: suspend (chainId: String) -> RuntimeSnapshot,
    private val remote: SigningMetadataRemote,
    /** Builds a runtime from V15 metadata bytes with the network's own type overrides. */
    private val buildRuntime: suspend (chainId: String, metadataV15: ByteArray, specVersion: Int) -> RuntimeSnapshot,
    private val exemptions: MetadataHashExemptions = MetadataHashExemptions.Default,
    private val computation: CoroutineDispatcher = Dispatchers.Default
) : SigningRuntimeSource {

    private class Entry(val specVersion: Int, val transactionVersion: Int, val signing: SigningRuntime.WithMetadataHash)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun signingRuntime(chain: IChain, runtimeVersion: RuntimeVersion): SigningRuntime {
        val runtime = currentRuntime(chain.id)
        if (CHECK_METADATA_HASH !in runtime.metadata.extrinsic.signedExtensions) return SigningRuntime.WithoutMetadataCheck(runtime)
        exemptions.reasonFor(chain.id)?.let { reason -> return SigningRuntime.MetadataHashExempt(runtime, reason) }

        return locks.getOrPut(chain.id) { Mutex() }.withLock {
            entries[chain.id]
                ?.takeIf { it.specVersion == runtimeVersion.specVersion && it.transactionVersion == runtimeVersion.transactionVersion }
                ?.signing
                ?: compute(chain.id, runtimeVersion).also { signing ->
                    entries[chain.id] = Entry(runtimeVersion.specVersion, runtimeVersion.transactionVersion, signing)
                }
        }
    }

    private suspend fun compute(chainId: String, runtimeVersion: RuntimeVersion): SigningRuntime.WithMetadataHash = try {
        val bytes = remote.metadataV15(chainId) ?: throw MetadataHashUnavailableException("The network serves no V15 metadata")
        val token = ChainTokenProperties.parse(remote.systemProperties(chainId))
        // Parsing, hashing and building take a few hundred milliseconds; never on the caller's (possibly main) thread.
        val (metadata, hash) = withContext(computation) {
            val metadata = FrameMetadataV15.decode(bytes)
            if (!ChainMetadataHash.advertises(metadata)) {
                throw MetadataHashUnavailableException("The network's V15 metadata does not check the metadata hash")
            }
            // The hash binds the spec version; metadata of another runtime version gives a hash the chain refuses.
            val version = ChainMetadataHash.runtimeVersion(metadata)
            if (version.specVersion != runtimeVersion.specVersion.toLong() || version.transactionVersion != runtimeVersion.transactionVersion.toLong()) {
                throw MetadataHashUnavailableException("The network's metadata is for another runtime version")
            }
            metadata to ChainMetadataHash.compute(metadata, token.decimals, token.symbol)
        }
        val runtime = withContext(computation) { buildRuntime(chainId, bytes, runtimeVersion.specVersion) }
        if (runtime.metadata.extrinsic.signedExtensions != metadata.extrinsic.signedExtensions.map { it.identifier }) {
            throw MetadataHashUnavailableException("The signing runtime does not match the hashed metadata")
        }
        SigningRuntime.WithMetadataHash(runtime, hash)
    } catch (error: CancellationException) {
        throw error
    } catch (error: MetadataHashUnavailableException) {
        throw error
    } catch (error: Exception) {
        throw MetadataHashUnavailableException("The network's metadata hash cannot be computed", error)
    }
}
