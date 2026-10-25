package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AssetBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetReader
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.data.repository.SelectedAccountAssetSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AssetBalanceUseCaseImpl(
    private val reader: CurrentAccountAssetReader,
    private val portfolioVisibility: PortfolioVisibility
) : AssetBalanceUseCase {
    override suspend fun invoke(accountMetaId: Long, chainId: String, assetId: String): AssetBalance = try {
        balance(reader.getSnapshot(accountMetaId), chainId, assetId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AssetBalance.Unavailable
    }

    override fun observe(accountMetaId: Long?, chainId: String, assetId: String): Flow<AssetBalance> {
        val snapshots = if (accountMetaId == null) reader.observeSelectedSnapshots() else reader.observeAccountSnapshots(accountMetaId)
        return snapshots.map { state ->
            when (state) {
                SelectedAccountAssetSnapshot.AwaitingSelection -> AssetBalance.AwaitingSelection
                is SelectedAccountAssetSnapshot.Loading -> AssetBalance.Loading(state.context.meta.id, state.context.fiat.currencyId, chainId, assetId)
                is SelectedAccountAssetSnapshot.Ready -> balance(state.snapshot, chainId, assetId)
            }
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(AssetBalance.Unavailable)
        }
    }

    private fun balance(snapshot: CurrentAccountAssetSnapshot, chainId: String, assetId: String): AssetBalance =
        calculateAssetBalance(snapshot, chainId, assetId, portfolioVisibility::isTestnet, portfolioVisibility::hasTrustedPrice)
}
