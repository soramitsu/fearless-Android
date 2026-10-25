package jp.co.soramitsu.fearless_utils.runtime.definitions.registry

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.BitVec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.CallBytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Data
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.EraType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.EventRecord
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericAccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericConsensus
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericConsensusEngineId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericMultiAddress
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericSeal
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericSealV0
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.H160
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.H256
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.H512
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.OpaqueCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SessionKeysSubstrate
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u16
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u256
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u64
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.stub.FakeType

typealias TypePresetBuilder = MutableMap<String, TypeReference>
typealias TypePreset = Map<String, TypeReference>

fun TypePreset.newBuilder(): TypePresetBuilder = toMutableMap()

fun TypePresetBuilder.type(type: Type<*>) {
    val currentRef = getOrCreate(type.name)

    currentRef.value = type
}

fun TypePresetBuilder.fakeType(name: String) {
    type(FakeType(name))
}

fun TypePresetBuilder.alias(alias: String, original: String) {
    val aliasedReference = getOrCreate(original)

    val typeAlias = Alias(alias, aliasedReference)

    type(typeAlias)
}

fun TypePresetBuilder.getOrCreate(definition: String) = getOrPut(definition) { TypeReference(null) }

fun TypePresetBuilder.create(definition: String): TypeReference =
    TypeReference(null).also { put(definition, it) }

fun createTypePresetBuilder(): TypePresetBuilder = mutableMapOf()

fun typePreset(builder: TypePresetBuilder.() -> Unit): TypePreset {
    return createTypePresetBuilder().apply(builder)
}

fun v14Preset() = typePreset {
    type(BooleanType)
    type(u8)
    type(u16)
    type(u32)
    type(u64)
    type(u128)
    type(u256)
    type(Bytes)
    type(Null)
    type(H256)

    type(GenericCall)
    type(GenericEvent)

    type(Data(this))
    type(GenericAccountId)
}

fun v13Preset(): TypePreset = typePreset {
    type(BooleanType)

    type(u8)
    type(u16)
    type(u32)
    type(u64)
    type(u128)
    type(u256)

    type(GenericAccountId)
    type(Null)
    type(GenericCall)

    fakeType("GenericBlock")

    type(H160)
    type(H256)
    type(H512)

    alias("GenericVote", "u8")

    type(Bytes)
    type(BitVec)

    type(Extrinsic)

    type(CallBytes) // seems to be unused in runtime
    type(EraType)
    type(Data(this))

    alias("BoxProposal", "Proposal")

    type(GenericConsensusEngineId)

    type(SessionKeysSubstrate(this))

    alias("GenericAccountIndex", "u32")

    type(GenericMultiAddress(this))

    type(OpaqueCall)

    type(GenericEvent)
    type(EventRecord(this))

    alias("<T::Lookup as StaticLookup>::Source", "LookupSource")
    alias("U64", "u64")
    alias("U32", "u32")

    alias("Bidkind", "BidKind")

    alias("AccountIdAddress", "GenericAccountId")

    alias("i128", "u128")

    alias("VoteWeight", "u128")
    alias("PreRuntime", "GenericPreRuntime")
    // todo replace with real type
    fakeType("GenericPreRuntime")
    type(GenericSealV0(this))
    type(GenericSeal(this))
    type(GenericConsensus(this))
}
