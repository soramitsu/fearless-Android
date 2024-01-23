package jp.co.soramitsu.runtime.multiNetwork.chain

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import java.math.BigInteger
import java.nio.ByteOrder
import jp.co.soramitsu.shared_utils.extensions.fromUnsignedBytes
import jp.co.soramitsu.shared_utils.scale.dataType.uint128
import jp.co.soramitsu.shared_utils.scale.utils.toUnsignedBytes

@JvmInline
value class ReefBalance(val value: BigInteger) {
    val planks: BigInteger
        get() {
            val bytes = value.toUnsignedBytes().fromUnsignedBytes(ByteOrder.LITTLE_ENDIAN).toByteArray()
            val newArray = ByteArray(16)
            bytes.copyInto(newArray)
            val reader = ScaleCodecReader(newArray)
            return uint128.read(reader)
        }
}