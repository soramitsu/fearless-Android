package co.jp.soramitsu.tonconnect.model

import jp.co.soramitsu.common.utils.safeParseCell
import org.ton.tlb.TlbCodec
import org.ton.tlb.TlbObject
import org.ton.tlb.loadTlb

inline fun <reified T : TlbObject> String.toTlb(): T? {
    val boc = safeParseCell() ?: return null
    return boc.parse { loadTlb(T::class.java.getMethod("tlbCodec").invoke(null) as TlbCodec<T>) }
}
