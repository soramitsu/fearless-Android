package jp.co.soramitsu.common.data.network.scale.dataType

import io.emeraldpay.polkaj.scale.ScaleReader
import io.emeraldpay.polkaj.scale.ScaleWriter

abstract class DataType<T> : ScaleReader<T>, ScaleWriter<T> {
    abstract fun conformsType(value: Any?): Boolean
}