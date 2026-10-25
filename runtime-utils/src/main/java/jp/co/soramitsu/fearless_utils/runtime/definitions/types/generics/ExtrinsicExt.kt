package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import java.util.Locale

class MultiSignature(val encryptionType: EncryptionType, val value: ByteArray)

fun Extrinsic.Signature.tryExtractMultiSignature(): MultiSignature? {
    val enumEntry = signature as? DictEnum.Entry<*> ?: return null
    val value = enumEntry.value as? ByteArray ?: return null

    val encryptionType =
        EncryptionType.fromStringOrNull(enumEntry.name.lowercase(Locale.ROOT)) ?: return null

    return MultiSignature(encryptionType, value)
}

private val EncryptionType.multiSignatureName
    get() = rawName.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }

fun MultiSignature.prepareForEncoding(): Any {
    return DictEnum.Entry(encryptionType.multiSignatureName, value)
}

fun <A> Extrinsic.Signature.Companion.new(
    accountIdentifier: A,
    signature: Any?,
    signedExtras: ExtrinsicPayloadExtrasInstance
) = Extrinsic.Signature(
    accountIdentifier = accountIdentifier,
    signature = signature,
    signedExtras = signedExtras
)

fun Extrinsic.Signature.Companion.newV27(
    accountId: ByteArray,
    signature: MultiSignature,
    signedExtras: ExtrinsicPayloadExtrasInstance
) = Extrinsic.Signature.new(
    accountIdentifier = accountId,
    signature = signature,
    signedExtras = signedExtras
)

fun Extrinsic.Signature.Companion.newV28(
    accountId: ByteArray,
    signature: MultiSignature,
    signedExtras: ExtrinsicPayloadExtrasInstance
) = Extrinsic.Signature.new(
    accountIdentifier = multiAddressFromId(accountId),
    signature = signature,
    signedExtras = signedExtras
)

fun multiAddressFromId(addressId: ByteArray): DictEnum.Entry<ByteArray> {
    return DictEnum.Entry(
        name = MULTI_ADDRESS_ID,
        value = addressId
    )
}
