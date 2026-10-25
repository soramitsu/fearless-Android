package jp.co.soramitsu.core.rpc

import jp.co.soramitsu.core.utils.toLongExact
import com.google.gson.Gson
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.scale.dataType.compactInt
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import java.math.BigInteger
import java.io.ByteArrayOutputStream

/** Read-only reconciliation under the wallet's configured RPC trust model, not a light-client proof. */
class FinalizedExtrinsicReader(private val rpcCalls: RpcCalls) {
    /** Captured before signing/output; older identical bytes cannot identify this new attempt. */
    suspend fun anchor(chainId: String): Anchor {
        val genesis = normalizeRpcBlockHash("0x" + chainId.removePrefix("0x"), "chain genesis").lowercase()
        return readAnchor(rpcCalls.socketFor(chainId), genesis)
    }

    /** A miss never proves non-execution. [beforeHeight] resumes an older bounded search. */
    suspend fun search(chainId: String, signedExtrinsic: String, beforeHeight: BigInteger? = null,
        limit: Int = 32, after: Anchor? = null): SearchResult {
        require(limit in 1..64)
        require(HEX_BYTES.matches(signedExtrinsic) && signedExtrinsic.length <= MAX_EXTRINSIC_LENGTH)
        require(beforeHeight == null || beforeHeight.signum() >= 0)
        val expectedGenesis = normalizeRpcBlockHash("0x" + chainId.removePrefix("0x"), "chain genesis").lowercase()
        val socket = rpcCalls.socketFor(chainId)
        val head = readAnchor(socket, expectedGenesis)
        val headHash = head.blockHash
        after?.let {
            check(it.genesisHash == expectedGenesis && it.number <= head.number && blockHash(socket, it.number) == it.blockHash) {
                "Original finalized anchor changed"
            }
        }
        val lowerBound = after?.number ?: BigInteger.valueOf(-1)
        var number = beforeHeight?.min(head.number) ?: head.number
        var expectedHash: String? = null
        var inclusion: Inclusion? = null
        var checked = 0
        while (checked < limit && number > lowerBound) {
            val currentHash = blockHash(socket, number)
            check(expectedHash == null || currentHash == expectedHash) { "Block ancestry changed" }
            val response = socket.read("chain_getBlock", currentHash) as? Map<*, *>
                ?: error("Block response is missing")
            val block = response["block"] as? Map<*, *> ?: error("Block body is missing")
            val current = header(block["header"], currentHash)
            check(current.number == number) { "Block number changed" }
            val extrinsics = block["extrinsics"] as? List<*> ?: error("Block extrinsics are missing")
            require(extrinsics.size <= 100_000)
            var matchedIndex: Int? = null
            extrinsics.forEachIndexed { index, raw ->
                val encoded = raw as? String ?: error("Extrinsic is not hex bytes")
                require(encoded.length <= MAX_EXTRINSIC_LENGTH && HEX_BYTES.matches(encoded))
                if (encoded.equals(signedExtrinsic, ignoreCase = true)) {
                    check(matchedIndex == null) { "Duplicate transaction in block response" }
                    matchedIndex = index
                }
            }
            checked++
            if (matchedIndex != null) {
                inclusion = Inclusion(expectedGenesis, signedExtrinsic.fromHex().blake2b256().toHexString(withPrefix = true),
                    currentHash, number, matchedIndex!!, headHash, head.number)
                break
            }
            expectedHash = current.parentHash
            number -= BigInteger.ONE
        }
        // A reconnect or inconsistent/pruned response must never turn into a terminal decision.
        val end = readAnchor(socket, expectedGenesis)
        check(end.number >= head.number &&
            blockHash(socket, head.number) == headHash) { "Finalized chain changed" }
        after?.let { check(blockHash(socket, it.number) == it.blockHash) { "Original finalized anchor changed" } }
        inclusion?.let { check(blockHash(socket, it.blockNumber) == it.blockHash) { "Included block changed" } }
        return SearchResult(inclusion, if (inclusion == null && number > lowerBound) number else null, checked)
    }

    data class Anchor(val genesisHash: String, val number: BigInteger, val blockHash: String) {
        init {
            require(number.signum() >= 0 && number.bitLength() <= 63)
            require(Regex("0x[0-9a-f]{64}").matches(genesisHash) && Regex("0x[0-9a-f]{64}").matches(blockHash))
        }
    }

    data class SearchResult(val inclusion: Inclusion?, val nextBeforeHeight: BigInteger?, val checkedBlocks: Int)

    /** Exact inclusion says nothing about the runtime call's dispatch success. */
    data class Inclusion(
        val genesisHash: String,
        val transactionHash: String,
        val blockHash: String,
        val blockNumber: BigInteger,
        val extrinsicIndex: Int,
        val finalizedHeadHash: String,
        val finalizedHeadNumber: BigInteger
    )

    private suspend fun blockHash(socket: SocketService, height: BigInteger): String =
        hash(socket.read("chain_getBlockHash", height.toLongExact()), "block hash")

    private suspend fun readAnchor(socket: SocketService, genesis: String): Anchor {
        check(blockHash(socket, BigInteger.ZERO) == genesis) { "Reconciliation network changed" }
        val headHash = hash(socket.read("chain_getFinalizedHead"), "finalized head")
        val head = header(socket.read("chain_getHeader", headHash), headHash)
        check(blockHash(socket, head.number) == headHash) { "Finalized head is not canonical" }
        check(blockHash(socket, BigInteger.ZERO) == genesis) { "Reconciliation network changed" }
        return Anchor(genesis, head.number, headHash)
    }

    private fun header(value: Any?, expectedHash: String): Header {
        val fields = value as? Map<*, *> ?: error("Block header is missing")
        val number = normalizeRpcBigInteger(fields["number"], "block number")
        require(number.bitLength() <= 63)
        val parent = hash(fields["parentHash"], "parent hash")
        val stateRoot = hash(fields["stateRoot"], "state root")
        val extrinsicsRoot = hash(fields["extrinsicsRoot"], "extrinsics root")
        val logs = (fields["digest"] as? Map<*, *>)?.get("logs") as? List<*> ?: error("Header digest is missing")
        require(logs.size <= 1024)
        val encoded = ByteArrayOutputStream()
        val writer = ScaleCodecWriter(encoded)
        encoded.write(parent.fromHex())
        compactInt.write(writer, number)
        encoded.write(stateRoot.fromHex())
        encoded.write(extrinsicsRoot.fromHex())
        compactInt.write(writer, logs.size.toBigInteger())
        logs.forEach { raw ->
            val log = raw as? String ?: error("Header digest is not hex bytes")
            require(log.length <= MAX_EXTRINSIC_LENGTH && HEX_BYTES.matches(log))
            encoded.write(log.fromHex())
        }
        check(encoded.toByteArray().blake2b256().toHexString(withPrefix = true) == expectedHash) { "Block header hash changed" }
        return Header(number, parent)
    }

    private fun hash(value: Any?, name: String): String = normalizeRpcBlockHash(value, name).lowercase()

    private suspend fun SocketService.read(method: String, vararg params: Any): Any? =
        executeAsync(RuntimeRequest(method, params.toList()), mapper = StrictResult)

    private data class Header(val number: BigInteger, val parentHash: String)

    private object StrictResult : ResponseMapper<Any?> {
        override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): Any? {
            check(rpcResponse.error == null) { "Reconciliation RPC failed" }
            return rpcResponse.result
        }
    }

    companion object {
        private const val MAX_EXTRINSIC_LENGTH = 1_000_000
        private val HEX_BYTES = Regex("0x(?:[0-9a-fA-F]{2})+")
    }
}
