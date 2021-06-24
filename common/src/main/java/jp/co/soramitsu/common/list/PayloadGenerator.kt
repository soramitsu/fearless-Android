package jp.co.soramitsu.common.list

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

typealias DiffCheck<T, V> = (T) -> V

open class PayloadGenerator<T>(private vararg val checks: DiffCheck<T, *>) {

    fun diff(first: T, second: T): List<DiffCheck<T, *>> {
        return checks.filter { check -> check(first) != check(second) }
    }
}

typealias UnknownPayloadHandler = (Any?) -> Unit

@Suppress("UNCHECKED_CAST")
fun <T, VH : RecyclerView.ViewHolder> ListAdapter<T, VH>.resolvePayload(
    holder: VH,
    position: Int,
    payloads: List<Any>,
    onUnknownPayload: UnknownPayloadHandler? = null,
    onDiffCheck: (DiffCheck<T, *>) -> Unit,
) {
    if (payloads.isEmpty()) {
        onBindViewHolder(holder, position)
    } else {
        when (val payload = payloads.first()) {
            is List<*> -> {
                val diffChecks = payload as List<DiffCheck<T, *>>

                diffChecks.forEach(onDiffCheck)
            }
            else -> onUnknownPayload?.invoke(payload)
        }
    }
}
