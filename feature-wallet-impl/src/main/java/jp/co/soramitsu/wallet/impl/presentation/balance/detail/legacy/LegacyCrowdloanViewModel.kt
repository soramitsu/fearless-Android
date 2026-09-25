package jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanRecovery
import jp.co.soramitsu.crowdloan.api.domain.shouldShowLegacyCrowdloan
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LegacyCrowdloanScreenState(
    val hasEvidence: Boolean,
    val description: String,
    val evidenceRows: List<TitleValueViewState>,
    val canOpenLockedBalance: Boolean
)

@HiltViewModel
class LegacyCrowdloanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletInteractor: WalletInteractor,
    private val recovery: LegacyCrowdloanRecovery,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val assetPayload: AssetPayload =
        savedStateHandle[LegacyCrowdloanFragment.KEY_ASSET_PAYLOAD]
            ?: error("No asset specified for legacy crowdloan")

    val state = MutableStateFlow<LoadingState<LegacyCrowdloanScreenState>>(LoadingState.Loading())
    val toolbarState = MutableStateFlow(
        MainToolbarViewState(
            title = resourceManager.getString(R.string.legacy_crowdloan_title),
            homeIconState = ToolbarHomeIconState.Navigation(R.drawable.ic_arrow_back_24dp),
            selectorViewState = ChainSelectorViewState()
        )
    )

    init {
        loadEvidence()
    }

    private fun loadEvidence() {
        launch {
            val loadedState = runCatching {
                val chain = walletInteractor.getChain(assetPayload.chainId)
                toolbarState.update { toolbar ->
                    toolbar.copy(
                        selectorViewState = ChainSelectorViewState(
                            selectedChainName = chain.name,
                            selectedChainId = chain.id
                        )
                    )
                }
                val asset = walletInteractor.getCurrentAsset(
                    chainId = assetPayload.chainId,
                    chainAssetId = assetPayload.chainAssetId
                )
                val evidence = recovery.findEvidence(
                    chainId = assetPayload.chainId,
                    chainAssetId = assetPayload.chainAssetId
                )
                val utilityAssetId = chain.assets.firstOrNull { it.isUtility }?.id
                val isVisible = shouldShowLegacyCrowdloan(
                    chainId = assetPayload.chainId,
                    chainAssetId = assetPayload.chainAssetId,
                    utilityAssetId = utilityAssetId,
                    evidence = evidence
                )

                val contributionAmount = asset.token.configuration
                    .amountFromPlanks(evidence.currentContributionAmount)
                    .formatCryptoDetail(asset.token.configuration.symbol)

                LegacyCrowdloanScreenState(
                    hasEvidence = isVisible,
                    description = if (isVisible) {
                        resourceManager.getString(R.string.legacy_crowdloan_description, chain.name)
                    } else {
                        resourceManager.getString(R.string.legacy_crowdloan_no_evidence)
                    },
                    evidenceRows = if (isVisible) {
                        listOf(
                            TitleValueViewState(
                                title = resourceManager.getString(R.string.legacy_crowdloan_current_contributions),
                                value = if (evidence.currentContributionCount > 0) {
                                    "${evidence.currentContributionCount} · $contributionAmount"
                                } else {
                                    "0"
                                }
                            ),
                            TitleValueViewState(
                                title = resourceManager.getString(R.string.legacy_crowdloan_historical_locks),
                                value = evidence.historicalLockCount.toString()
                            ),
                            TitleValueViewState(
                                title = resourceManager.getString(R.string.legacy_crowdloan_historical_claims),
                                value = evidence.historicalClaimCount.toString()
                            )
                        )
                    } else {
                        emptyList()
                    },
                    canOpenLockedBalance = isVisible && asset.locked > BigDecimal.ZERO
                )
            }.getOrElse {
                LegacyCrowdloanScreenState(
                    hasEvidence = false,
                    description = resourceManager.getString(R.string.legacy_crowdloan_no_evidence),
                    evidenceRows = emptyList(),
                    canOpenLockedBalance = false
                )
            }

            state.update { LoadingState.Loaded(loadedState) }
        }
    }

    fun backClicked() = router.back()

    fun openLockedBalance() {
        launch {
            val asset = walletInteractor.getCurrentAsset(
                chainId = assetPayload.chainId,
                chainAssetId = assetPayload.chainAssetId
            )
            router.openFrozenTokens(
                FrozenAssetPayload(
                    assetSymbol = asset.token.configuration.symbol,
                    locked = asset.locked,
                    reserved = asset.reserved,
                    redeemable = asset.redeemable
                )
            )
        }
    }
}
