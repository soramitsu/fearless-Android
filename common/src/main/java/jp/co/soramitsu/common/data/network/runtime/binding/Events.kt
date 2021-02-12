package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import java.math.BigInteger

class EventRecord<E>(val phase: Phase, val event: E)

sealed class Phase {

    class ApplyExtrinsic(val extrinsicId: BigInteger) : Phase()

    object Finalization : Phase()

    object Initialization : Phase()
}

@HelperBinding
fun <E> bindEventRecord(
    dynamicInstance: Any?,
    eventBinder: (GenericEvent.Instance) -> E
): EventRecord<E> {
    requireType<Struct.Instance>(dynamicInstance)

    val phaseDynamic = dynamicInstance.getOfType<DictEnum.Entry<*>>("phase")

    val phase = when (phaseDynamic.name) {
        "ApplyExtrinsic" -> Phase.ApplyExtrinsic(phaseDynamic.value.cast())
        "Finalization" -> Phase.Finalization
        "Initialization" -> Phase.Initialization
        else -> incompatible()
    }

    val dynamicEvent = dynamicInstance.getOfType<GenericEvent.Instance>("event")

    val event = eventBinder(dynamicEvent)

    return EventRecord(phase, event)
}