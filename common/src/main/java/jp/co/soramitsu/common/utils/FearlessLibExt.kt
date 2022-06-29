package jp.co.soramitsu.common.utils

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.common.data.network.runtime.binding.bindNullableNumberConstant
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumberConstant
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.seed.SeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.fromUnsignedBytes
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.dataType.DataType
import jp.co.soramitsu.fearless_utils.scale.dataType.uint32
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import java.io.ByteArrayOutputStream

val BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH: String
    get() = "//44//60//0/0/0"

fun BIP32JunctionDecoder.default() = decode(DEFAULT_DERIVATION_PATH)

fun StorageEntry.defaultInHex() = default.toHexString(withPrefix = true)

fun <T> DataType<T>.fromHex(hex: String): T {
    val codecReader = ScaleCodecReader(hex.fromHex())

    return read(codecReader)
}

fun <T> DataType<T>.toHex(value: T): String {
    return toByteArray(value).toHexString(withPrefix = true)
}

fun <T> DataType<T>.toByteArray(value: T): ByteArray {
    val stream = ByteArrayOutputStream()
    val writer = ScaleCodecWriter(stream)

    write(writer, value)

    return stream.toByteArray()
}

typealias StructBuilderWithContext<S> = S.(EncodableStruct<S>) -> Unit

operator fun <S : Schema<S>> S.invoke(block: StructBuilderWithContext<S>? = null): EncodableStruct<S> {
    val struct = EncodableStruct(this)

    block?.invoke(this, struct)

    return struct
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
    get() = event.index

fun Module.constant(name: String) = constantOrNull(name) ?: throw NoSuchElementException()

fun Module.numberConstant(name: String, runtimeSnapshot: RuntimeSnapshot) = bindNumberConstant(constant(name), runtimeSnapshot)
fun Module.optionalNumberConstant(name: String, runtimeSnapshot: RuntimeSnapshot) = bindNullableNumberConstant(constant(name), runtimeSnapshot)

fun Module.constantOrNull(name: String) = constants[name]

fun RuntimeMetadata.staking() = module(Modules.STAKING)

fun RuntimeMetadata.parachainStaking() = module(Modules.PARACHAIN_STAKING)

fun RuntimeMetadata.system() = module(Modules.SYSTEM)

fun RuntimeMetadata.tokens() = module(Modules.TOKENS)

fun RuntimeMetadata.balances() = module(Modules.BALANCES)

fun RuntimeMetadata.crowdloan() = module(Modules.CROWDLOAN)

fun RuntimeMetadata.babe() = module(Modules.BABE)

fun RuntimeMetadata.slots() = module(Modules.SLOTS)

fun RuntimeMetadata.session() = module(Modules.SESSION)

fun RuntimeMetadata.identity() = module(Modules.SESSION)

fun <T> StorageEntry.storageKeys(runtime: RuntimeSnapshot, singleMapArguments: Collection<T>): Map<String, T> {
    return singleMapArguments.associateBy { storageKey(runtime, it) }
}

inline fun <K, T> StorageEntry.storageKeys(
    runtime: RuntimeSnapshot,
    singleMapArguments: Collection<T>,
    argumentTransform: (T) -> K
): Map<String, K> {
    return singleMapArguments.associateBy(
        keySelector = { storageKey(runtime, it) },
        valueTransform = { argumentTransform(it) }
    )
}

fun RuntimeMetadata.hasModule(name: String) = moduleOrNull(name) != null

private const val HEX_SYMBOLS_PER_BYTE = 2
private const val UINT_32_BYTES = 4

fun String.u32ArgumentFromStorageKey() = uint32.fromHex(takeLast(HEX_SYMBOLS_PER_BYTE * UINT_32_BYTES)).toLong().toBigInteger()

fun ByteArray.decodeToInt() = fromUnsignedBytes().toInt()

fun SeedFactory.createSeed32(length: Mnemonic.Length, password: String?) = cropSeedTo32Bytes(createSeed(length, password))

// fun SeedFactory.deriveSeed32(mnemonicWords: String, password: String?) = cropSeedTo32Bytes(deriveSeed(mnemonicWords, password))

fun SubstrateSeedFactory.deriveSeed32(mnemonicWords: String, password: String?) = cropSeedTo32Bytes(deriveSeed(mnemonicWords, password))

fun EthereumSeedFactory.deriveSeed32(mnemonicWords: String, password: String?) = deriveSeed(mnemonicWords, password)

private fun cropSeedTo32Bytes(seedResult: SeedFactory.Result): SeedFactory.Result {
    return SeedFactory.Result(seed = seedResult.seed.copyOfRange(0, 32), seedResult.mnemonic)
}

object Modules {
    const val STAKING = "Staking"
    const val PARACHAIN_STAKING = "ParachainStaking"
    const val BALANCES = "Balances"
    const val SYSTEM = "System"
    const val CROWDLOAN = "Crowdloan"
    const val BABE = "Babe"
    const val SLOTS = "Slots"
    const val SESSION = "Session"
    const val TOKENS = "Tokens"
    const val IDENTITY = "Identity"
}
