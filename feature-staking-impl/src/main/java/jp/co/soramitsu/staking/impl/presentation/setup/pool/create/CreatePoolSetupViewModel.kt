package jp.co.soramitsu.staking.impl.presentation.setup.pool.create

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.model.address
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.StakeInsufficientBalanceException
import jp.co.soramitsu.common.validation.MinPoolCreationThresholdException
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CreatePoolSetupViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val poolInteractor: StakingPoolInteractor,
    private val stakingInteractor: StakingInteractor,
    private val router: StakingRouter
) : BaseViewModel(), CreatePoolSetupScreenInterface {

    companion object {
        private const val NOMINATOR_WALLET_SELECTOR_TAG = "nominator"
        private const val STATE_TOGGLER_WALLET_SELECTOR_TAG = "state_toggler"
    }

    private val asset: Asset
    private val address: String
    private val accountId: AccountId
    private val chain: Chain
    private val initialAmount: String

    init {
        val mainState = stakingPoolSharedStateProvider.requireMainState

        asset = mainState.requireAsset
        address = mainState.requireAddress
        accountId = mainState.accountId
        chain = mainState.requireChain
        initialAmount = mainState.requireAmount.format()
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
        ButtonViewState(resourceManager.getString(R.string.common_create))
    )

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

    private val feeInfoViewState = combine(
        poolIdFlow,
        enteredPoolNameFlow,
        enteredAmountFlow,
        selectedNominatorFlow,
        selectedStateTogglerFlow
    ) { poolId, poolName, enteredAmount, selectedNominator, selectedStateToggler ->
        val amountInDecimal = enteredAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val amountInPlanks = asset.token.planksFromAmount(amountInDecimal)
        val feeInPlanks = poolInteractor.estimateCreateFee(poolId.toBigInteger(), poolName, amountInPlanks, address, selectedNominator, selectedStateToggler)
        feeInPlanksFlow.value = feeInPlanks
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    val viewState: StateFlow<CreatePoolSetupViewState> = jp.co.soramitsu.common.utils.combine(
        amountInputViewState,
        poolIdFlow,
        poolNameInputStateFlow,
        addressDisplayFlow,
        feeInfoViewState,
        nominatorDisplayFlow,
        stateTogglerDisplayFlow
    ) { amountInputState, poolId, poolNameInput, currentAddressDisplay, feeInfo, selectedNominator, selectedStateToggler ->
        val isButtonEnabled =
            poolNameInput.text.isNotEmpty() && amountInputState.tokenAmount.toBigDecimalOrNull() != null &&
                feeInfo.feeAmount.isNullOrEmpty().not()

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
        router.back()
    }

    override fun onPoolNameInput(text: String) {
        enteredPoolNameFlow.value = text
    }

    override fun onTokenAmountInput(text: String) {
        enteredAmountFlow.value = text
    }

    override fun onNominatorClick() {
        router.openWalletSelector(NOMINATOR_WALLET_SELECTOR_TAG)
    }

    override fun onStateTogglerClick() {
        router.openWalletSelector(STATE_TOGGLER_WALLET_SELECTOR_TAG)
    }

    override fun onCreateClick() {
        viewModelScope.launch {
            val amount = enteredAmountFlow.value.toBigDecimalOrNull().orZero()
            val amountInPlanks = asset.token.planksFromAmount(amount)

            val existentialDeposit = stakingInteractor.existentialDeposit(chain.id)
            val fee = feeInPlanksFlow.value
            val transferableInPlanks = asset.token.planksFromAmount(asset.transferable)

            val minToCreate = poolInteractor.getMinToCreate(chain.id)
            if (amountInPlanks < minToCreate) {
                val minToCreateFormatted = asset.token.amountFromPlanks(minToCreate).formatTokenAmount(asset.token.configuration)
                showError(MinPoolCreationThresholdException(resourceManager, minToCreateFormatted))
                return@launch
            }

            if ((amountInPlanks + fee + existentialDeposit) > transferableInPlanks) {
                showError(StakeInsufficientBalanceException(resourceManager))
                return@launch
            }

            val poolName = enteredPoolNameFlow.value
            val poolId = poolIdFlow.value
            val nominatorAddress = selectedNominatorFlow.value
            val stateTogglerAddress = selectedStateTogglerFlow.value

            val createFlow = stakingPoolSharedStateProvider.createFlowState
            createFlow.mutate { nullableState ->
                val state = requireNotNull(nullableState)
                state.copy(
                    poolName = poolName,
                    amountInPlanks = amountInPlanks,
                    poolId = poolId,
                    nominatorAddress = nominatorAddress,
                    stateTogglerAddress = stateTogglerAddress
                )
            }
            router.openCreatePoolConfirm()
        }
    }

    private fun onWalletSelected(item: WalletSelectorPayload) {
        viewModelScope.launch {
            val (tag, selectedWalletId) = item
            val metaAccount = stakingInteractor.getMetaAccount(selectedWalletId)
            val address = metaAccount.address(chain) ?: return@launch
            when (tag) {
                NOMINATOR_WALLET_SELECTOR_TAG -> {
                    selectedNominatorFlow.value = address
                }
                STATE_TOGGLER_WALLET_SELECTOR_TAG -> {
                    selectedStateTogglerFlow.value = address
                }
            }
        }
    }
}
