package jp.co.soramitsu.staking.impl.presentation.setup.pool.create

import jp.co.soramitsu.staking.impl.presentation.common.requireStakingBalance

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.MAX_DECIMALS_8
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.MinPoolCreationThresholdException
import jp.co.soramitsu.common.validation.StakeInsufficientBalanceException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeEstimation
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolCreateFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CreatePoolSetupViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val poolInteractor: StakingPoolInteractor,
    private val stakingInteractor: StakingInteractor,
    private val router: StakingRouter,
    private val quickInputsUseCase: QuickInputsUseCase
) : BaseViewModel(), CreatePoolSetupScreenInterface {

    companion object {
        private const val NOMINATOR_WALLET_SELECTOR_TAG = "nominator"
        private const val STATE_TOGGLER_WALLET_SELECTOR_TAG = "state_toggler"
    }

    private val createFlow = stakingPoolSharedStateProvider.createFlowState
    private val originalDraft = createFlow.capture()
    private val mainReview = requireNotNull(originalDraft.value).context

    private val review = stakingPoolSharedStateProvider.poolContextReview(mainReview)
    private var formRevision = Any()
    private val pendingSelections = mutableMapOf<String, Any>()
    private val selectionsPendingFlow = MutableStateFlow(false)

    private val asset: Asset
    private val address: String
    private val accountId: AccountId
    private val chain: Chain
    private val initialAmount: BigDecimal
    private var quickInputs: Map<Double, BigDecimal> = mapOf()

    init {
        val mainState = requireNotNull(mainReview.value)

        asset = mainState.requireAsset
        address = mainState.requireAddress
        accountId = mainState.accountId
        chain = mainState.requireChain
        initialAmount = mainState.requireAmount

        viewModelScope.launch {
            quickInputs = quickInputsUseCase.calculateStakingQuickInputs(
                chain.id,
                asset.token.configuration.id,
                calculateAvailableAmount = { asset.transferable },
                calculateFee = {
                    poolInteractor.estimateCreateFee(
                        chain,
                        asset.metaId,
                        512.toBigInteger(),
                        this@CreatePoolSetupViewModel.javaClass.name,
                        it,
                        address,
                        address,
                        address
                    )
                })
        }
    }

    private val defaultPoolNameInputState = TextInputViewState("", resourceManager.getString(R.string.pool_staking_pool_name))

    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
        fiatAmount = "",
        tokenAmount = initialAmount
    )

    private val defaultScreenState = CreatePoolSetupViewState(
        defaultPoolNameInputState,
        defaultAmountInputState,
        "...",
        "...",
        "...",
        "...",
        "...",
        FeeInfoViewState.default,
        ButtonViewState(resourceManager.getString(R.string.common_create), enabled = false)
    )

    private val enteredAmountFlow = MutableStateFlow(initialAmount)

    private val amountInputViewState: Flow<AmountInputViewState> = enteredAmountFlow.map { amount ->
        val tokenBalance = asset.transferable?.formatCrypto(asset.token.configuration.symbol)
            ?: resourceManager.getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable)
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = amount
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val poolIdFlow = flowOf {
        poolInteractor.getLastPoolId(asset.token.configuration.chainId).toInt() + 1
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val selectedNominatorFlow = MutableStateFlow(address)
    private val selectedStateTogglerFlow = MutableStateFlow(address)

    private val addressDisplayFlow = flowOf {
        poolInteractor.getAccountName(address) ?: address
    }.inBackground()

    private val nominatorDisplayFlow = selectedNominatorFlow.map {
        poolInteractor.getAccountName(it) ?: it
    }.inBackground()

    private val stateTogglerDisplayFlow = selectedStateTogglerFlow.map {
        poolInteractor.getAccountName(it) ?: it
    }.inBackground()

    private val enteredPoolNameFlow = MutableStateFlow("")

    private val poolNameInputStateFlow = enteredPoolNameFlow.map {
        defaultPoolNameInputState.copy(text = it)
    }

    private val feeInPlanksFlow: MutableStateFlow<BigInteger> = MutableStateFlow(BigInteger.ZERO)
    private val feeEstimation = FeeEstimation(resourceManager)

    private val feeInfoViewState = jp.co.soramitsu.common.utils.combine(
        poolIdFlow,
        enteredPoolNameFlow,
        enteredAmountFlow,
        selectedNominatorFlow,
        selectedStateTogglerFlow,
        feeEstimation.retryFlow
    ) { poolId, poolName, enteredAmount, selectedNominator, selectedStateToggler, _ ->
        val amountInPlanks = asset.token.planksFromAmount(enteredAmount)
        // Runs eagerly in viewModelScope: a failed estimation (on Polkadot and Kusama pools moved to Asset Hub) must
        // neither crash the app nor stop later ones.
        val feeInPlanks = feeEstimation.estimate {
            poolInteractor.estimateCreateFee(chain, asset.metaId, poolId.toBigInteger(), poolName, amountInPlanks, address, selectedNominator, selectedStateToggler)
        } ?: return@combine FeeInfoViewState.default
        feeInPlanksFlow.value = feeInPlanks
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatCryptoDetail(asset.token.configuration.symbol)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    val viewState: StateFlow<CreatePoolSetupViewState> = jp.co.soramitsu.common.utils.combine(
        amountInputViewState,
        poolIdFlow,
        poolNameInputStateFlow,
        addressDisplayFlow,
        feeInfoViewState,
        nominatorDisplayFlow,
        stateTogglerDisplayFlow,
        feeEstimation.failureFlow,
        selectionsPendingFlow
    ) { amountInputState, poolId, poolNameInput, currentAddressDisplay, feeInfo, selectedNominator, selectedStateToggler, feeFailure, selectionsPending ->
        // Without a fee the button stays enabled when its estimation failed, so that tapping it says why.
        val isButtonEnabled = asset.transferable != null && !selectionsPending && (feeFailure != null || (
            poolNameInput.text.isNotEmpty() && amountInputState.tokenAmount > BigDecimal.ZERO &&
                feeInfo.feeAmount.isNullOrEmpty().not()
            ))

        CreatePoolSetupViewState(
            poolNameInputViewState = poolNameInput,
            amountInputViewState = amountInputState,
            poolId = poolId.toString(),
            depositor = currentAddressDisplay,
            root = currentAddressDisplay,
            nominator = selectedNominator,
            stateToggler = selectedStateToggler,
            feeInfoViewState = feeInfo,
            createButtonViewState = ButtonViewState(resourceManager.getString(R.string.common_create), isButtonEnabled)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    init {
        viewModelScope.launch {
            router.walletSelectorPayloadFlow.collect { payload ->
                payload?.let { onWalletSelected(it) }
            }
        }
    }

    override fun onNavigationClick() {
        if (review.revoke()) router.back()
    }

    fun onDismissed() {
        review.revoke()
    }

    override fun onCleared() {
        review.revoke()
        super.onCleared()
    }

    override fun onPoolNameInput(text: String) {
        review.withCurrent {
            if (enteredPoolNameFlow.value != text) {
                formRevision = Any()
                enteredPoolNameFlow.value = text
            }
        }
    }

    override fun onTokenAmountInput(value: BigDecimal?) {
        review.withCurrent {
            if (enteredAmountFlow.value != value.orZero()) {
                formRevision = Any()
                enteredAmountFlow.value = value.orZero()
            }
        }
    }

    override fun onNominatorClick() {
        review.withCurrent {
            pendingSelections.remove(NOMINATOR_WALLET_SELECTOR_TAG)
            selectionsPendingFlow.value = pendingSelections.isNotEmpty()
            formRevision = Any()
            router.openWalletSelector(NOMINATOR_WALLET_SELECTOR_TAG)
        }
    }

    override fun onStateTogglerClick() {
        review.withCurrent {
            pendingSelections.remove(STATE_TOGGLER_WALLET_SELECTOR_TAG)
            selectionsPendingFlow.value = pendingSelections.isNotEmpty()
            formRevision = Any()
            router.openWalletSelector(STATE_TOGGLER_WALLET_SELECTOR_TAG)
        }
    }

    private data class SetupRequest(
        val draft: StakingPoolSharedState.Snapshot<StakingPoolCreateFlowState>,
        val formRevision: Any,
        val fields: StakingPoolCreateFlowState?,
        val fee: BigInteger
    )

    private fun SetupRequest.isCurrent(): Boolean =
        formRevision === this@CreatePoolSetupViewModel.formRevision && pendingSelections.isEmpty() &&
            draft.isSameFlowAs(originalDraft) && createFlow.isEditable(draft)

    /** An error belongs to the same form, draft, wallet and screen as its validation. */
    private fun present(request: SetupRequest, operation: () -> Unit) {
        review.withCurrent { if (request.isCurrent()) operation() }
    }

    override fun onCreateClick() {
        val request = review.withCurrent {
            val draft = createFlow.capture()
            SetupRequest(draft, formRevision, draft.value?.copy(
                poolName = enteredPoolNameFlow.value,
                amountInPlanks = asset.token.planksFromAmount(enteredAmountFlow.value),
                poolId = poolIdFlow.value,
                nominatorAddress = selectedNominatorFlow.value,
                stateTogglerAddress = selectedStateTogglerFlow.value
            ), feeInPlanksFlow.value)
        } ?: return
        val fields = request.fields ?: return
        if (!request.isCurrent()) return
        viewModelScope.launch {
            try {
                if (!review.isCurrent() || !request.isCurrent()) return@launch
                if (feeEstimation.hasFailed) {
                    present(request) { showError(feeEstimation.missingFeeError()) }
                    return@launch
                }
                val existentialDeposit = stakingInteractor.existentialDeposit(chain.id)
                currentCoroutineContext().ensureActive()
                if (!review.isCurrent() || !request.isCurrent()) return@launch
                val minToCreate = poolInteractor.getMinToCreate(chain.id)
                currentCoroutineContext().ensureActive()
                if (!review.isCurrent() || !request.isCurrent()) return@launch
                val amountInPlanks = fields.requireAmountInPlanks
                if (amountInPlanks < minToCreate) {
                    val minToCreateFormatted = minToCreate.formatCryptoDetailFromPlanks(asset.token.configuration)
                    present(request) { showError(MinPoolCreationThresholdException(resourceManager, minToCreateFormatted)) }
                    return@launch
                }
                val transferableInPlanks = asset.token.planksFromAmount(requireStakingBalance(asset.transferable, resourceManager))
                if ((amountInPlanks + request.fee + existentialDeposit) > transferableInPlanks) {
                    present(request) { showError(StakeInsufficientBalanceException(resourceManager)) }
                    return@launch
                }
                present(request) {
                    if (stakingPoolSharedStateProvider.publishCreateSetup(request.draft, fields)) {
                        review.withCurrent {
                            if (formRevision === request.formRevision) router.openCreatePoolConfirm()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                present(request) { showError(error) }
            }
        }
    }

    override fun onQuickAmountInput(value: Double) {
        val amount = quickInputs[value] ?: return
        onTokenAmountInput(amount.setScale(MAX_DECIMALS_8, RoundingMode.HALF_DOWN))
    }

    private fun onWalletSelected(item: WalletSelectorPayload) {
        val (tag, selectedWalletId) = item
        if (tag != NOMINATOR_WALLET_SELECTOR_TAG && tag != STATE_TOGGLER_WALLET_SELECTOR_TAG) return
        val request = review.withCurrent {
            Any().also {
                pendingSelections[tag] = it
                selectionsPendingFlow.value = true
                formRevision = Any()
            }
        } ?: return
        viewModelScope.launch {
            try {
                if (review.withCurrent { pendingSelections[tag] === request } != true) return@launch
                val metaAccount = stakingInteractor.getMetaAccount(selectedWalletId)
                currentCoroutineContext().ensureActive()
                val address = metaAccount.address(chain) ?: return@launch
                review.withCurrent {
                    if (pendingSelections[tag] !== request) return@withCurrent
                    val target = if (tag == NOMINATOR_WALLET_SELECTOR_TAG) selectedNominatorFlow else selectedStateTogglerFlow
                    if (target.value != address) {
                        formRevision = Any()
                        target.value = address
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                review.withCurrent {
                    if (pendingSelections[tag] === request) showError(error)
                }
            } finally {
                review.withCurrent {
                    if (pendingSelections[tag] === request) {
                        pendingSelections.remove(tag)
                        selectionsPendingFlow.value = pendingSelections.isNotEmpty()
                    }
                }
            }
        }
    }
}
