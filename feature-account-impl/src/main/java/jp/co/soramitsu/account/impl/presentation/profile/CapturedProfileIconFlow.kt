package jp.co.soramitsu.account.impl.presentation.profile

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.account.capturedPortfolioDetailFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** An icon is usable only with the exact portfolio snapshot for which it was generated. */
data class CapturedProfileIcon(val source: TotalBalance, val icon: Any?)

/** Reuses the shared revocation protocol; synchronous mapping adds no asynchronous boundary. */
fun capturedProfileIconFlow(
    balances: Flow<TotalBalance>,
    resolveIcon: suspend (MetaAccount) -> Any
): Flow<CapturedProfileIcon> = capturedPortfolioDetailFlow(balances) { source ->
    resolveIcon(source.meta)
}.map { CapturedProfileIcon(it.source, it.detail) }
