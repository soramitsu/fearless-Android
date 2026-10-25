package jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.scale.dataType.compactInt
import java.math.BigInteger

class Compact(name: String) : NumberType(name) {

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): BigInteger {
        return compactInt.read(scaleCodecReader)
    }

    override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: BigInteger) {
        return compactInt.write(scaleCodecWriter, value)
    }
}
