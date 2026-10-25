package jp.co.soramitsu.fearless_utils.runtime.definitions.types.stub

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type

class FakeType(name: String) : Type<Nothing>(name) {

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): Nothing {
        throw IllegalArgumentException("Fake")
    }

    override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: Nothing) {
        throw IllegalArgumentException("Fake")
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return false
    }

    override val isFullyResolved = true
}
