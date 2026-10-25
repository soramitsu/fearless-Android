package jp.co.soramitsu.fearless_utils.encrypt

sealed class SignatureWrapper(val encryptionType: EncryptionType) {
    abstract val signature: ByteArray

    class Ecdsa(
        val v: ByteArray,
        val r: ByteArray,
        val s: ByteArray
    ) : SignatureWrapper(EncryptionType.ECDSA) {

        override val signature: ByteArray = r + s + v
    }

    class Sr25519(override val signature: ByteArray) : SignatureWrapper(EncryptionType.SR25519)

    class Ed25519(override val signature: ByteArray) : SignatureWrapper(EncryptionType.ED25519)
}

val SignatureWrapper.Ecdsa.vByte: Byte
    get() = v[0]
