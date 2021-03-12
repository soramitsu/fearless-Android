package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import java.math.BigInteger

sealed class MultiAddress(val enumIndex: Int) {
    class Id(val value: ByteArray) : MultiAddress(0)
    class Index(val value: BigInteger) : MultiAddress(1)
    class Raw(val value: ByteArray) : MultiAddress(2)
    class Address32(val value: ByteArray) : MultiAddress(3)
    class Address20(val value: ByteArray) : MultiAddress(4)
}

fun bindMultiAddress(multiAddress: MultiAddress): DictEnum.Entry<*> {
    return when (multiAddress) {
        is MultiAddress.Id -> multiAddressFromId(multiAddress.value)
        is MultiAddress.Index -> DictEnum.Entry(name = "Index", value = multiAddress.value)
        is MultiAddress.Raw -> DictEnum.Entry(name = "Raw", value = multiAddress.value)
        is MultiAddress.Address32 -> DictEnum.Entry(name = "Address32", value = multiAddress.value)
        is MultiAddress.Address20 -> DictEnum.Entry(name = "Address20", value = multiAddress.value)
    }
}
