package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.hash.FrameMetadataV15
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

/** What a node says about the runtime a transaction is signed for. Nothing here is trusted: the chain checks the hash. */
interface SigningMetadataRemote {
    /** The `RuntimeMetadataPrefixed` V15 bytes, or null when the runtime does not provide V15. */
    suspend fun metadataV15(chainId: String): ByteArray?

    /** The `system_properties` JSON object. */
    suspend fun systemProperties(chainId: String): Map<*, *>
}

/** [SigningMetadataRemote] over the network's current RPC connection. */
class RpcSigningMetadataRemote(private val socketFor: (String) -> SocketService) : SigningMetadataRemote {

    override suspend fun metadataV15(chainId: String): ByteArray? {
        // `Metadata_metadata_at_version(15u32)`: the version as a little-endian u32. A new request has a new id.
        val request = RuntimeRequest("state_call", listOf("Metadata_metadata_at_version", "0x0f000000"))
        val result = socketFor(chainId).executeAsync(request, mapper = pojo<String>().nonNull())
        return FrameMetadataV15.unwrapVersionedResult(result.fromHex())
    }

    override suspend fun systemProperties(chainId: String): Map<*, *> =
        socketFor(chainId).executeAsync(RuntimeRequest("system_properties", listOf()), mapper = pojo<Map<*, *>>().nonNull())
}
