package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Frozen SCALE transaction: key acquisition and authorization happen after preparation. */
class PreparedExtrinsic internal constructor(
    private val runtime: RuntimeSnapshot,
    private val encryption: MultiChainEncryption,
    private val signatureConstructor: Type.InstanceConstructor<SignatureWrapper>,
    private val signatureType: Type<*>,
    private val version: Byte,
    address: ByteArray,
    extras: ByteArray,
    call: ByteArray,
    payload: ByteArray
) {
    private val address = address.copyOf()
    private val extras = extras.copyOf()
    private val call = call.copyOf()
    private val payload = payload.copyOf()

    /** Binds account encoding, version, call, mortality, nonce, tip and runtime identity. */
    fun intentBytes(): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { writer ->
            writer.writeUTF("FearlessWallet-PreparedExtrinsic-v1")
            writer.writeByte(version.toInt())
            writer.writeUTF(when (encryption) {
                is MultiChainEncryption.Substrate -> "substrate:${encryption.encryptionType.name}"
                MultiChainEncryption.Ethereum -> "ethereum:ECDSA"
            })
            writer.writeInt(address.size)
            writer.write(address)
            writer.writeInt(payload.size)
            writer.write(payload)
        }
    }.toByteArray()

    /** Call only within the captured intent's synchronous signing authorization boundary. */
    fun sign(
        keypair: Keypair,
        signer: (MultiChainEncryption, ByteArray, Keypair) -> SignatureWrapper = Signer::sign
    ): String {
        val message = if (payload.size > 256) payload.blake2b256() else payload.copyOf()
        val wrapper = signer(encryption, message, keypair)
        val signature = signatureConstructor.constructInstance(runtime.typeRegistry, wrapper)
        val signatureBytes = signatureType.bytes(runtime, signature)
        return Bytes.toHex(runtime, byteArrayOf(version) + address + signatureBytes + extras + call)
    }
}
