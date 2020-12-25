@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.scale.dataType.DataType
import jp.co.soramitsu.fearless_utils.scale.dataType.byteArraySized
import jp.co.soramitsu.fearless_utils.scale.dataType.uint8

private const val ECDSA_SIGNATURE_SIZE = 65
private const val OTHER_SIGNATURE_SIZE = 64

class Signature(val version: UByte, val value: ByteArray) {

    constructor(encryptionType: EncryptionType, value: ByteArray) :
        this(encryptionType.signatureVersion.toUByte(), value)
}

object SignatureType : DataType<Signature>() {
    override fun conformsType(value: Any?) = value is Signature

    override fun read(reader: ScaleCodecReader): Signature {
        val signatureVersion = uint8.read(reader)

        val signature = byteArraySized(signatureSize(signatureVersion)).read(reader)

        return Signature(signatureVersion, signature)
    }

    override fun write(writer: ScaleCodecWriter, value: Signature) {
        uint8.write(writer, value.version)

        byteArraySized(signatureSize(value.version)).write(writer, value.value)
    }

    private fun signatureSize(signatureVersion: UByte): Int {
        return when (signatureVersion.toInt()) {
            EncryptionType.ECDSA.signatureVersion -> ECDSA_SIGNATURE_SIZE
            else -> OTHER_SIGNATURE_SIZE
        }
    }
}