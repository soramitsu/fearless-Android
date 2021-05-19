package jp.co.soramitsu.common.utils

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumberConstant
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.metadata.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.dataType.DataType
import jp.co.soramitsu.fearless_utils.scale.dataType.uint32
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import java.io.ByteArrayOutputStream

fun ByteArray.toAddress(networkType: Node.NetworkType) = toAddress(networkType.runtimeConfiguration.addressByte)

fun <T> DataType<T>.fromHex(hex: String): T {
    val codecReader = ScaleCodecReader(hex.fromHex())

    return read(codecReader)
}

fun <T> DataType<T>.toByteArray(value: T): ByteArray {
    val stream = ByteArrayOutputStream()
    val writer = ScaleCodecWriter(stream)

    write(writer, value)

    return stream.toByteArray()
}

fun <S : Schema<S>> EncodableStruct<S>.hash(): String {
    return schema.toByteArray(this).blake2b256().toHexString(withPrefix = true)
}

fun String.extrinsicHash(): String {
    return fromHex().blake2b256().toHexString(withPrefix = true)
}

fun String.toHexAccountId(): String = toAccountId().toHexString()

fun preBinder() = pojo<String>().nonNull()

val GenericEvent.Instance.index
    get() = moduleIndex to eventIndex

fun Module.constant(name: String) = constantOrNull(name) ?: throw NoSuchElementException()

fun Module.numberConstant(name: String, runtimeSnapshot: RuntimeSnapshot) = bindNumberConstant(constant(name), runtimeSnapshot)

fun Module.constantOrNull(name: String) = constants[name]

fun RuntimeMetadata.staking() = module(Modules.STAKING)

fun RuntimeMetadata.system() = module(Modules.SYSTEM)

fun RuntimeMetadata.balances() = module(Modules.BALANCES)

fun RuntimeMetadata.crowdloan() = module(Modules.CROWDLOAN)

fun String.networkType() = Node.NetworkType.findByAddressByte(addressByte())!!

fun RuntimeMetadata.hasModule(name: String) = moduleOrNull(name) != null

private const val HEX_SYMBOLS_PER_BYTE = 2
private const val UINT_32_BYTES = 4

fun String.u32ArgumentFromStorageKey() = uint32.fromHex(takeLast(HEX_SYMBOLS_PER_BYTE * UINT_32_BYTES)).toLong().toBigInteger()

object Modules {
    const val STAKING = "Staking"
    const val BALANCES = "Balances"
    const val SYSTEM = "System"
    const val CROWDLOAN = "Crowdloan"
}
