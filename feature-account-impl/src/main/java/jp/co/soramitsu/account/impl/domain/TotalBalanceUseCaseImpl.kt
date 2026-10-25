package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetReader
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.data.repository.SelectedAccountAssetSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class TotalBalanceUseCaseImpl(
    private val accountRepository: AccountRepository,
    private val reader: CurrentAccountAssetReader,
    private val portfolioVisibility: PortfolioVisibility
) : TotalBalanceUseCase {

    /** A completed historical read retains its account and currency even if selection changes. */
    override suspend operator fun invoke(metaId: Long?): TotalBalance = try {
        val id = metaId ?: accountRepository.getSelectedMetaAccount().id
        total(reader.getSnapshot(id))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        TotalBalance.Unavailable
    }

    override fun observe(metaId: Long?): Flow<TotalBalance> {
        val snapshots = if (metaId == null) reader.observeSelectedSnapshots() else reader.observeAccountSnapshots(metaId)
        return snapshots.map { state ->
            when (state) {
                SelectedAccountAssetSnapshot.AwaitingSelection -> TotalBalance.AwaitingSelection
                is SelectedAccountAssetSnapshot.Loading -> TotalBalance.Loading(state.context.meta.id, state.context.fiat.currencyId)
                is SelectedAccountAssetSnapshot.Ready -> total(state.snapshot)
            }
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(TotalBalance.Unavailable)
        }
    }

    private fun total(snapshot: CurrentAccountAssetSnapshot): TotalBalance = calculateTrustedTotalBalance(
        snapshot, portfolioVisibility::isTestnet, portfolioVisibility::hasTrustedPrice
    )
}
