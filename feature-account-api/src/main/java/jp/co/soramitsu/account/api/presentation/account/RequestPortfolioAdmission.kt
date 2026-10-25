package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import kotlinx.coroutines.CancellationException

/** A request stays bound to its original wallet; a changed/missing binding revokes its display. */
fun admitRequestPortfolio(
    source: TotalBalance,
    requestMetaId: Long?,
    currentBindingMetaId: Long?,
    supportsRequest: (TotalBalance.Ready) -> Boolean
): TotalBalance {
    if (requestMetaId == null || currentBindingMetaId != requestMetaId) return TotalBalance.Unavailable
    return when (source) {
        is TotalBalance.Ready -> {
            if (source.meta.id != requestMetaId) return TotalBalance.Unavailable
            try {
                source.takeIf(supportsRequest) ?: TotalBalance.Unavailable
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                TotalBalance.Unavailable
            }
        }
        is TotalBalance.Loading -> source.takeIf { it.metaId == requestMetaId } ?: TotalBalance.Unavailable
        else -> source
    }
}

/** Recheck after every suspending request lookup before using a reviewed portfolio. */
fun isCurrentRequestPortfolio(
    captured: TotalBalance.Ready,
    current: TotalBalance,
    requestMetaId: Long?,
    actionMetaId: Long?
): Boolean = current === captured && requestMetaId != null &&
    actionMetaId == requestMetaId && captured.meta.id == requestMetaId
