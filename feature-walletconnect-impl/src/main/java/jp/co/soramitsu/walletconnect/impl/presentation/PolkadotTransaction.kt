package jp.co.soramitsu.walletconnect.impl.presentation

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The runtime a Substrate network's WalletConnect transactions are decoded and encoded with. Null when it is not
 * available now: nothing is signed then, and the user can retry.
 */
internal typealias PolkadotRuntimeSource = suspend (Chain) -> RuntimeSnapshot?

private const val RUNTIME_TIMEOUT_MILLIS = 10_000L

/** The chain registry's runtime, waited for a bounded time while the network connects. */
internal fun chainRegistryRuntimeSource(chainRegistry: ChainRegistry): PolkadotRuntimeSource = { chain ->
    withTimeoutOrNull(RUNTIME_TIMEOUT_MILLIS) { chainRegistry.awaitRuntimeProvider(chain.id).get() }
}

/** The network's runtime is not loaded yet. Unlike a refusal, the request stays pending and signing can be retried. */
internal class PolkadotRuntimeUnavailableException : IllegalStateException("The network's runtime is not available")

/** Why the wallet refuses to sign a Polkadot transaction, for the preview and for the dApp's error response. */
internal enum class PolkadotRefusal(val reason: String) {
    OtherNetwork("The transaction is for another network"),
    NeverExpires("The transaction never expires"),
    OtherRuntime("The transaction was built for another runtime version"),
    UnsupportedFormat("The transaction format of this network is not supported"),
    UndecodableCall("The call cannot be decoded with the network's runtime"),
    RetiredDestination("The cross-chain transfer would not arrive")
}

/** A refused Polkadot transaction: the dApp gets an error and the preview says why, before any key is read. */
internal class PolkadotTransactionRefusedException(
    val refusal: PolkadotRefusal,
    detail: String? = null
) : WalletConnectRequestRejectedException(listOfNotNull(refusal.reason, detail).joinToString(": "))

internal fun refuseTransaction(refusal: PolkadotRefusal, detail: String? = null): Nothing =
    throw PolkadotTransactionRefusedException(refusal, detail)

internal inline fun requireTransaction(
    condition: Boolean,
    refusal: PolkadotRefusal,
    detail: () -> String? = { null }
) {
    if (!condition) refuseTransaction(refusal, detail())
}

/**
 * A `polkadot_signTransaction` request read against its network's runtime: the call it signs, decoded exactly, and
 * the payload bytes the signature commits to, encoded from the decoded fields. The preview shows this transaction
 * and the signer signs exactly its [signingPayload], so what the user approves is what is signed.
 */
internal class PolkadotTransaction private constructor(
    val payload: PolkadotSignPayload,
    val call: GenericCall.Instance,
    /** The cross-chain calls it dispatches; none of them goes to a destination that no longer delivers. */
    val crossChainCalls: List<CrossChainCall>,
    val signingPayload: ByteArray
) {
    companion object {
        /** Refuses with [PolkadotTransactionRefusedException] whatever cannot be signed exactly as it is shown. */
        fun read(
            chain: Chain,
            payload: PolkadotSignPayload,
            runtime: RuntimeSnapshot
        ): PolkadotTransaction {
            requireSignableWithoutRuntime(chain, payload)
            val call = decodeCall(payload.method, runtime) ?: refuseTransaction(PolkadotRefusal.UndecodableCall)
            val crossChainCalls = PolkadotCrossChainCalls(chain).read(call)
            crossChainCalls.firstNotNullOfOrNull { it.retirement }?.let { retirement ->
                refuseTransaction(PolkadotRefusal.RetiredDestination, retirement.reason)
            }
            val signingPayload = PolkadotSigningPayload.encode(payload, runtime)
            return PolkadotTransaction(payload, call, crossChainCalls, signingPayload)
        }

        /** The checks that need no runtime, so a request for another network is refused even while offline. */
        fun requireSignableWithoutRuntime(chain: Chain, payload: PolkadotSignPayload) {
            requireTransaction(isGenesisHashOf(payload.genesisHash, chain), PolkadotRefusal.OtherNetwork)
            // A signature that never expires stays valid for as long as the dApp keeps it.
            requireTransaction(!payload.isImmortal, PolkadotRefusal.NeverExpires)
        }

        /**
         * The call [method] encodes, or null unless the bytes are exactly one call: nothing may be left over and
         * encoding the decoded call must give the same bytes back. Bytes appended to a call are how raw fields
         * could spell another transaction's payload, so a call that does not decode exactly is never signed.
         */
        fun decodeCall(method: ByteArray, runtime: RuntimeSnapshot): GenericCall.Instance? = runCatching {
            val reader = ScaleCodecReader(method)
            val call = GenericCall.decode(reader, runtime)
            call.takeIf { !reader.hasNext() && GenericCall.toByteArray(runtime, it).contentEquals(method) }
        }.getOrNull()
    }
}

/**
 * The signature as polkadot.js `signPayload` returns it: a `MultiSignature`, whose first byte names the key type
 * (Ed25519 0, Sr25519 1, ECDSA 2), followed by the signature.
 */
internal fun SignatureWrapper.multiSignature(): ByteArray = byteArrayOf(
    when (encryptionType) {
        EncryptionType.ED25519 -> MULTI_SIGNATURE_ED25519
        EncryptionType.SR25519 -> MULTI_SIGNATURE_SR25519
        EncryptionType.ECDSA -> MULTI_SIGNATURE_ECDSA
    }
) + signature

private const val MULTI_SIGNATURE_ED25519: Byte = 0
private const val MULTI_SIGNATURE_SR25519: Byte = 1
private const val MULTI_SIGNATURE_ECDSA: Byte = 2
