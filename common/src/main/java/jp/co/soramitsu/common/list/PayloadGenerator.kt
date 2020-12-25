package jp.co.soramitsu.common.list

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

typealias DiffCheck<T, V> = (T) -> V

open class PayloadGenerator<T>(vararg val checks: DiffCheck<T, *>) {

    fun diff(first: T, second: T): List<DiffCheck<T, *>> {
        return checks.filter { check -> check(first) != check(second) }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T, VH : RecyclerView.ViewHolder> ListAdapter<T, VH>.resolvePayload(
    holder: VH,
    position: Int,
    payloads: MutableList<Any>,
    onDiffCheck: (DiffCheck<T, *>) -> Unit
) {
    if (payloads.isEmpty()) {
        onBindViewHolder(holder, position)
    } else {
        val diffChecks = payloads.first() as List<DiffCheck<T, *>>

        diffChecks.forEach(onDiffCheck)
    }
}