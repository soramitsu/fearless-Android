package jp.co.soramitsu.fearless_utils.scale.dataType

import io.emeraldpay.polkaj.scale.ScaleReader
import io.emeraldpay.polkaj.scale.ScaleWriter

abstract class DataType<T> : ScaleReader<T>, ScaleWriter<T> {
    abstract fun conformsType(value: Any?): Boolean
}
