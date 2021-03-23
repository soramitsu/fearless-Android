package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import java.math.BigInteger

sealed class MultiAddress(val enumIndex: Int) {
    companion object {
        const val TYPE_ID = "Id"
        const val TYPE_INDEX = "Index"
        const val TYPE_RAW = "Raw"
        const val TYPE_ADDRESS32 = "Address32"
        const val TYPE_ADDRESS20 = "Address20"
    }

    class Id(val value: ByteArray) : MultiAddress(0)
    class Index(val value: BigInteger) : MultiAddress(1)
    class Raw(val value: ByteArray) : MultiAddress(2)
    class Address32(val value: ByteArray) : MultiAddress(3)
    class Address20(val value: ByteArray) : MultiAddress(4)
}

fun bindMultiAddress(multiAddress: MultiAddress): DictEnum.Entry<*> {
    return when (multiAddress) {
        is MultiAddress.Id -> DictEnum.Entry(MultiAddress.TYPE_ID, multiAddress.value)
        is MultiAddress.Index -> DictEnum.Entry(MultiAddress.TYPE_INDEX, multiAddress.value)
        is MultiAddress.Raw -> DictEnum.Entry(MultiAddress.TYPE_RAW, multiAddress.value)
        is MultiAddress.Address32 -> DictEnum.Entry(MultiAddress.TYPE_ADDRESS32, multiAddress.value)
        is MultiAddress.Address20 -> DictEnum.Entry(MultiAddress.TYPE_ADDRESS20, multiAddress.value)
    }
}

fun bindMultiAddress(dynamicInstance: DictEnum.Entry<*>): MultiAddress {
    return when (dynamicInstance.name) {
        MultiAddress.TYPE_ID -> MultiAddress.Id(dynamicInstance.value.cast())
        MultiAddress.TYPE_INDEX -> MultiAddress.Index(dynamicInstance.value.cast())
        MultiAddress.TYPE_RAW -> MultiAddress.Raw(dynamicInstance.value.cast())
        MultiAddress.TYPE_ADDRESS32 -> MultiAddress.Address32(dynamicInstance.value.cast())
        MultiAddress.TYPE_ADDRESS20 -> MultiAddress.Address20(dynamicInstance.value.cast())
        else -> incompatible()
    }
}

fun bindMultiAddressId(dynamicInstance: DictEnum.Entry<*>) = (bindMultiAddress(dynamicInstance) as? MultiAddress.Id)?.value
