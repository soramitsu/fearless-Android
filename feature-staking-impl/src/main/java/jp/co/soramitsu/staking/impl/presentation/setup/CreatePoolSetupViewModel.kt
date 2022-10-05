package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.setup.compose.CreatePoolSetupViewState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CreatePoolSetupViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val poolInteractor: StakingPoolInteractor,
    private val getTotalBalanceUseCase: GetTotalBalanceUseCase,
    private val iconGenerator: AddressIconGenerator
) : BaseViewModel() {

    companion object {
        private const val NOMINATOR_WALLET_SELECTOR_TAG = "nominator"
        private const val STATE_TOGGLER_WALLET_SELECTOR_TAG = "state_toggler"
    }

    private val asset: Asset
    private val address: String
    private val accountId: AccountId
    private val chain: Chain

    init {
        val mainState = stakingPoolSharedStateProvider.mainState
        val createFlowState = stakingPoolSharedStateProvider.createFlowState

        asset = requireNotNull(mainState.get()?.asset)
        address = requireNotNull(mainState.get()?.address)
        accountId = requireNotNull(mainState.get()?.accountId)
        chain = requireNotNull(mainState.get()?.chain)
    }

    private val minToCreate: String = BigDecimal.TEN.format()

    private val defaultPoolNameInoutState = TextInputViewState("", resourceManager.getString(R.string.pool_staking_pool_name))

    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
        fiatAmount = "",
        tokenAmount = minToCreate
    )

    private val defaultWalletSelectorState = WalletSelectorViewState(emptyList(), null)

    private val defaultScreenState = CreatePoolSetupViewState(
        defaultPoolNameInoutState,
        defaultAmountInputState,
        "...",
        "...",
        "...",
        "...",
        "...",
        FeeInfoViewState.default,
        ButtonViewState(resourceManager.getString(R.string.common_create)),
        defaultWalletSelectorState
    )

    private val enteredAmountFlow = MutableStateFlow(minToCreate)

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

    private val poolIdFlow = flowOf {
        poolInteractor.getLastPoolId(asset.token.configuration.chainId).toInt() + 1
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val addressDisplayFlow = flowOf {
        poolInteractor.getAccountName(address) ?: address
    }

    private val enteredPoolNameFlow = MutableStateFlow("")

    private val poolNameInputStateFlow = enteredPoolNameFlow.map {
        defaultPoolNameInoutState.copy(text = it)
    }

    private val lightAccountsFlow = poolInteractor.getLightAccounts().map { lightAccounts ->
        lightAccounts.map { lightAccount ->
            val lightAccountAddress = chain.addressOf(lightAccount.substrateAccountId)

            val balanceModel = getTotalBalanceUseCase(lightAccount.id).first()
            val icon = iconGenerator.createAddressIcon(lightAccount.substrateAccountId, AddressIconGenerator.SIZE_BIG)
            WalletItemViewState(
                id = lightAccount.id,
                title = lightAccount.name,
                isSelected = lightAccountAddress == currentSelectedAddress,
                walletIcon = icon,
                balance = balanceModel.balance.formatAsCurrency(balanceModel.fiatSymbol),
                changeBalanceViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs().formatAsCurrency(balanceModel.fiatSymbol)
                )
            )
        }
    }.launchIn(viewModelScope)


    private val walletSelectorViewState: MutableStateFlow<Pair<String, WalletSelectorViewState>?> = MutableStateFlow(null)

    private val selectedNominatorFlow = MutableStateFlow(address)
    private val selectedStateTogglerFlow = MutableStateFlow(address)

    private val feeInfoViewState = combine(
        enteredAmountFlow,
        selectedNominatorFlow,
        selectedStateTogglerFlow
    ) { enteredAmount, selectedNominator, selectedStateToggler ->
        val amountInDecimal = enteredAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val amountInPlanks = asset.token.planksFromAmount(amountInDecimal)
        val feeInPlanks = poolInteractor.estimateCreateFee(amountInPlanks, address, selectedNominator, selectedStateToggler)
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    val viewState: StateFlow<CreatePoolSetupViewState> = combine(
        amountInputViewState,
        poolIdFlow,
        poolNameInputStateFlow,
        addressDisplayFlow,
        feeInfoViewState
    ) { amountInputState, poolId, poolNameInput, currentAddressDisplay, feeInfo ->
        val isButtonEnabled =
            poolNameInput.text.isNotEmpty() && amountInputState.tokenAmount.toBigDecimalOrNull() != null &&
                feeInfo.feeAmount.isNullOrEmpty().not()

        CreatePoolSetupViewState(
            poolNameInputViewState = poolNameInput,
            amountInputViewState = amountInputState,
            poolId = poolId.toString(),
            depositor = currentAddressDisplay,
            root = currentAddressDisplay,
            nominator = currentAddressDisplay,
            stateToggler = currentAddressDisplay,
            feeInfoViewState = feeInfo,
            createButtonViewState = ButtonViewState(resourceManager.getString(R.string.common_create), isButtonEnabled),
            walletSelectorViewState = defaultWalletSelectorState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    fun onPoolNameInput(poolName: String) {
        enteredPoolNameFlow.value = poolName
    }

    fun onAmountInput(amount: String) {
        enteredAmountFlow.value = amount
    }

    fun onNominatorClick() {

    }

    fun onStateTogglerClick() {

    }
}
