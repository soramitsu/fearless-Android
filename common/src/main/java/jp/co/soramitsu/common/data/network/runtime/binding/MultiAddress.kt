package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum

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
