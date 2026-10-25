package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.presentation.account.withAllHoldings
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.presentation.account.capturedPortfolioListFlow
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel
class WalletSelectorViewModel @Inject constructor(
    accountRepository: AccountRepository,
    addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val resourceManager: ResourceManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val tag = savedStateHandle.get<String>(WalletSelectorFragment.TAG_ARGUMENT_KEY)!!
    private val selectedWalletId =
        savedStateHandle.get<Long?>(WalletSelectorFragment.SELECTED_WALLET_ID)
    private val walletSelectionMode = savedStateHandle[WalletSelectorFragment.WALLET_SELECTION_MODE]
        ?: WalletSelectionMode.CurrentWallet

    private val walletItemsFlow = combine(
        capturedPortfolioListFlow(
            accountRepository.lightMetaAccountsFlow().map { wallets -> wallets.map { it.id } },
            balances = { metaId -> totalBalanceUseCase.observe(metaId) },
            resolveDetail = { ready ->
                addressIconGenerator.createAddressIcon(
                    ready.meta.supportedEcosystemWithIconAddress(),
                    AddressIconGenerator.SIZE_BIG
                )
            }
        ),
        nomisScoreInteractor.observeNomisScores()
            .onStart { emit(emptyList()) }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                emit(emptyList())
            }
    ) { rows, scores ->
        rows.map { row ->
            val ready = row.portfolio.source as? TotalBalance.Ready
            WalletItemViewState(
                id = row.metaId,
                title = ready?.meta?.name.orEmpty(),
                isSelected = ready?.meta?.isSelected == true,
                walletIcon = row.portfolio.detail ?: R.drawable.ic_wallet,
                score = if (ready == null) null else scores.firstOrNull { it.metaId == row.metaId }?.score
            ).withAllHoldings(row.portfolio.source, resourceManager)
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(emptyList())
        showError(failure)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state = walletItemsFlow.map { walletItems ->
        val selectedWallet = walletItems.firstOrNull { it.id == selectedWalletId }
        WalletSelectorViewState(
            wallets = walletItems,
            selectedWallet = when (walletSelectionMode) {
                WalletSelectionMode.CurrentWallet -> selectedWallet ?: walletItems.firstOrNull { it.isSelected }
                WalletSelectionMode.ExternalSelectedWallet -> selectedWallet
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WalletSelectorViewState(emptyList(), null))

    fun onWalletSelected(item: WalletItemViewState) {
        viewModelScope.launch {
            router.setWalletSelectorPayload(WalletSelectorPayload(tag, item.id))
            router.backWithResult(WalletSelectorFragment.RESULT_ADDRESS to item.id)
        }
    }

    fun onBackClicked() {
        router.back()
    }
}
