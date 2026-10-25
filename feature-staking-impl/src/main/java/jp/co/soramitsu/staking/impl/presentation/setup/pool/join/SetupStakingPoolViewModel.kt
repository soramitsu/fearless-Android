package jp.co.soramitsu.staking.impl.presentation.setup.pool.join

import jp.co.soramitsu.staking.impl.presentation.common.requireStakingBalance

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AccountInfoViewState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.AmountTooLowToStakeException
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedWarning
import jp.co.soramitsu.common.validation.ExistentialDepositUnknownException
import jp.co.soramitsu.common.validation.StakeInsufficientBalanceException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeEstimation
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SetupStakingPoolViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingInteractor: StakingInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val quickInputsUseCase: QuickInputsUseCase
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset
    private val address: String
    private val initialAmount: BigDecimal
    private val joinScreen = stakingPoolSharedStateProvider.joinFlowState.capture()
    private val mainReview = joinScreen.value?.context ?: stakingPoolSharedStateProvider.mainState.capture()
    private var reviewActive = true
    private var quickInputs: Map<Double, BigDecimal> = emptyMap()

    init {
        val mainState = requireNotNull(mainReview.value)

        chain = requireNotNull(mainState.chain)
        asset = requireNotNull(mainState.asset)
        address = mainState.requireAddress
        initialAmount = mainState.requireAmount

        viewModelScope.launch {
            quickInputs = quickInputsUseCase.calculateStakingQuickInputs(
                chain.id,
                asset.token.configuration.id,
                calculateAvailableAmount = { asset.transferable },
                calculateFee = { stakingPoolInteractor.estimateJoinFee(chain, address, it, asset.metaId) })
        }
    }

    private val toolbarViewState = ToolbarViewState(resourceManager.getString(R.string.pool_staking_join_title), R.drawable.ic_arrow_back_24dp)

    private val accountInfoViewStateFlow: Flow<AccountInfoViewState> = flow {
        val meta = stakingInteractor.getCurrentMetaAccount()
        if (!stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) || meta.id != asset.metaId || meta.address(chain) != address) return@flow
        val icon = iconGenerator.createAddressIcon(
            chain.isEthereumBased,
            address,
            AddressIconGenerator.SIZE_BIG
        )
        val state = AccountInfoViewState(
            accountName = meta.name,
            address = address,
            image = icon,
            caption = resourceManager.getString(R.string.pool_staking_join_account_title)
        )
        emit(state)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAccountInfoState)

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
            tokenAmount = amount,
            precision = asset.token.configuration.precision
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeEstimation = FeeEstimation(resourceManager)

    // Runs eagerly in viewModelScope for every amount: a failed estimation (on Polkadot and Kusama pools moved to
    // Asset Hub) must neither crash the app nor stop later ones.
    private val feeInPlanksFlow = combine(enteredAmountFlow, feeEstimation.retryFlow) { amount, _ ->
        val inPlanks = asset.token.planksFromAmount(amount)
        feeEstimation.estimate { stakingPoolInteractor.estimateJoinFee(chain, address, inPlanks, asset.metaId) }
    }.inBackground().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = feeInPlanksFlow.filterNotNull().map { feeInPlanks ->
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatCryptoDetail(asset.token.configuration.symbol)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val buttonStateFlow = combine(enteredAmountFlow, stakingPoolSharedStateProvider.joinFlowState.submissionInProgress) { amount, pending ->
        val amountInPlanks = asset.token.planksFromAmount(amount)
        ButtonViewState(
            resourceManager.getString(R.string.pool_staking_join_button_title),
            asset.transferable != null && amountInPlanks != BigInteger.ZERO && !pending
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    val viewState = combine(
        accountInfoViewStateFlow,
        amountInputViewState,
        feeInfoViewStateFlow,
        buttonStateFlow
    ) { accountInfoViewState, amountInputViewState, feeViewState, buttonState ->
        SetupStakingScreenViewState(
            toolbarViewState,
            accountInfoViewState,
            amountInputViewState,
            feeViewState,
            buttonState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    fun onNavigationClick() {
        if (!reviewActive) return
        onDismissed()
        router.back()
    }

    /** Discards callbacks from a dismissed setup screen without consuming its editable draft. */
    fun onDismissed() {
        reviewActive = false
    }

    fun onAmountEntered(amount: BigDecimal?) {
        enteredAmountFlow.value = amount.orZero()
    }

    fun onNextClick() {
        if (!reviewActive) return
        val joinFlow = stakingPoolSharedStateProvider.joinFlowState
        val review = joinFlow.capture()
        val setupFlow = review.value ?: return router.returnToMain()
        if (!stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) || !review.isSameFlowAs(joinScreen) || !joinFlow.isEditable(review)) return
        val amount = enteredAmountFlow.value

        viewModelScope.launch {
            isValid(amount).fold(
                onSuccess = {
                    if (reviewActive && stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) && joinFlow.setIfCurrent(review, setupFlow.copy(amount = amount))) {
                        router.openSelectPool()
                    }
                },
                onFailure = { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    if (!reviewActive || !stakingPoolSharedStateProvider.isJoinContextCurrent(mainReview) || !joinFlow.isEditable(review)) return@fold
                    val message =
                        throwable.localizedMessage ?: throwable.message ?: resourceManager.getString(R.string.common_undefined_error_message)
                    val errorAlertViewState = (throwable as? ValidationException)?.let { (title, message) ->
                        AlertViewState(
                            title = title,
                            message = message,
                            buttonText = resourceManager.getString(R.string.common_ok),
                            iconRes = R.drawable.ic_status_warning_16
                        )
                    } ?: AlertViewState(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = message,
                        buttonText = resourceManager.getString(R.string.common_got_it),
                        iconRes = R.drawable.ic_status_warning_16
                    )
                    router.openAlert(errorAlertViewState)
                })
        }
    }

    private suspend fun isValid(amount: BigDecimal): Result<Any> {
        return runCatching {
            // Checked before the pool limits are read: where pools moved to Asset Hub the relay chain no longer
            // stores them, and that lookup would fail instead of the explanation.
            val feeInPlanks = feeInPlanksFlow.value ?: throw feeEstimation.missingFeeError()
            val amountInPlanks = asset.token.planksFromAmount(amount)
            val transferableInPlanks = asset.token.planksFromAmount(requireStakingBalance(asset.transferable, resourceManager))
            val minToJoinInPlanks = stakingPoolInteractor.getMinToJoinPool(chain.id)
            val minToJoinFormatted = minToJoinInPlanks.formatCryptoDetailFromPlanks(asset.token.configuration)
            val existentialDeposit = existentialDepositUseCase(asset.token.configuration).getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                throw ExistentialDepositUnknownException(resourceManager, asset.token.configuration.symbol.uppercase())
            }

            when {
                amountInPlanks + feeInPlanks > transferableInPlanks -> throw StakeInsufficientBalanceException(resourceManager)
                transferableInPlanks - amountInPlanks - feeInPlanks <= existentialDeposit -> throw ExistentialDepositCrossedWarning(resourceManager, existentialDeposit.formatCryptoDetailFromPlanks(asset.token.configuration))
                amountInPlanks < minToJoinInPlanks -> throw AmountTooLowToStakeException(resourceManager, minToJoinFormatted)
                else -> Unit
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    fun onQuickAmountInput(value: Double) {
        val amount = quickInputs[value] ?: return
        enteredAmountFlow.value  = amount.setScale(asset.token.configuration.precision, RoundingMode.HALF_DOWN)
    }

    private val defaultState
        get() = SetupStakingScreenViewState(
            toolbarViewState,
            defaultAccountInfoState,
            defaultAmountInputState,
            FeeInfoViewState.default,
            defaultButtonState
        )

    private val defaultAmountInputState
        get() = AmountInputViewState(
            tokenName = "...",
            tokenImage = "",
            totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
            fiatAmount = "",
            tokenAmount = initialAmount
        )

    private val defaultAccountInfoState
        get() = AccountInfoViewState(
            accountName = "...",
            address = "",
            image = R.drawable.ic_wallet,
            caption = resourceManager.getString(R.string.pool_staking_join_account_title)
        )

    private val defaultButtonState
        get() = ButtonViewState(
            resourceManager.getString(R.string.pool_staking_join_button_title),
            true
        )
}
