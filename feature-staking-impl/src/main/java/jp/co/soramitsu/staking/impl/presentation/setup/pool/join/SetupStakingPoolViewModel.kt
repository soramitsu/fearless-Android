package jp.co.soramitsu.staking.impl.presentation.setup.pool.join

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.AmountTooLowToStakeException
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedException
import jp.co.soramitsu.common.validation.StakeInsufficientBalanceException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
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
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class SetupStakingPoolViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingInteractor: StakingInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val existentialDepositUseCase: ExistentialDepositUseCase
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset
    private val initialAmount: String

    init {
        val mainState = stakingPoolSharedStateProvider.requireMainState

        chain = requireNotNull(mainState.chain)
        asset = requireNotNull(mainState.asset)
        initialAmount = mainState.requireAmount.format()
    }

    private val toolbarViewState = ToolbarViewState(resourceManager.getString(R.string.pool_staking_join_title), R.drawable.ic_arrow_back_24dp)

    private val accountInfoViewStateFlow: Flow<AccountInfoViewState> = flow {
        val meta = stakingInteractor.getCurrentMetaAccount()
        val address = meta.address(chain) ?: ""
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

    private val amountInputViewState: Flow<AmountInputViewState> = enteredAmountFlow.map { enteredAmount ->
        val tokenBalance = asset.transferable.formatTokenAmount(asset.token.configuration)
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = enteredAmount
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeInPlanksFlow = enteredAmountFlow.map { enteredAmount ->
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val inPlanks = asset.token.planksFromAmount(amount)
        stakingPoolInteractor.estimateJoinFee(inPlanks)
    }.inBackground().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = feeInPlanksFlow.filterNotNull().map { feeInPlanks ->
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val buttonStateFlow = enteredAmountFlow.map { enteredAmount ->
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val amountInPlanks = asset.token.planksFromAmount(amount)
        ButtonViewState(
            resourceManager.getString(R.string.pool_staking_join_button_title),
            amountInPlanks != BigInteger.ZERO
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
        router.back()
    }

    fun onAmountEntered(amount: String) {
        enteredAmountFlow.value = amount.replace(',', '.')
    }

    fun onNextClick() {
        val setupFlow = stakingPoolSharedStateProvider.requireJoinState
        val amount = enteredAmountFlow.value.toBigDecimalOrNull().orZero()

        viewModelScope.launch {
            isValid(amount).fold({
                stakingPoolSharedStateProvider.joinFlowState.set(setupFlow.copy(amount = amount))
                router.openSelectPool()
            }, { throwable ->
                val message =
                    throwable.localizedMessage ?: throwable.message ?: resourceManager.getString(R.string.common_undefined_error_message)
                val errorAlertViewState = (throwable as? ValidationException)?.let { (title, message) ->
                    AlertViewState(
                        title = title,
                        message = message,
                        buttonText = resourceManager.getString(R.string.common_got_it),
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
        val amountInPlanks = asset.token.planksFromAmount(amount)
        val transferableInPlanks = asset.token.planksFromAmount(asset.transferable)
        val minToJoinInPlanks = stakingPoolInteractor.getMinToJoinPool(chain.id)
        val minToJoinFormatted = asset.token.amountFromPlanks(minToJoinInPlanks).formatTokenAmount(asset.token.configuration)
        val existentialDeposit = existentialDepositUseCase(asset.token.configuration)
        val feeInPlanks = feeInPlanksFlow.value ?: return Result.failure(WaitForFeeCalculationException(resourceManager))

        return when {
            amountInPlanks + feeInPlanks >= transferableInPlanks -> Result.failure(StakeInsufficientBalanceException(resourceManager))
            transferableInPlanks - amountInPlanks - feeInPlanks <= existentialDeposit -> Result.failure(ExistentialDepositCrossedException(resourceManager))
            amountInPlanks < minToJoinInPlanks -> Result.failure(AmountTooLowToStakeException(resourceManager, minToJoinFormatted))
            else -> Result.success(Unit)
        }
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
