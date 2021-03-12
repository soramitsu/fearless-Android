package jp.co.soramitsu.feature_staking_impl.presentation.staking

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.RewardEstimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

private const val PERIOD_MONTH = 30
private const val PERIOD_YEAR = 365

class ReturnsModel(
    val monthly: RewardEstimation,
    val yearly: RewardEstimation
)

class StakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val stakingSharedState: StakingSharedState
) : BaseViewModel() {

    val currentStakingState = interactor.selectedAccountStakingState()
        .flowOn(Dispatchers.Default)
        .share()

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    private val currentAsset = interactor.currentAssetFlow()

    val asset = currentAsset.map { mapAssetToAssetModel(it, resourceManager) }.asLiveData()

    val enteredAmountFlow = MutableStateFlow(stakingSharedState.amount.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val amountFiat = parsedAmountFlow.combine(currentAsset) { amount, asset -> asset.token.fiatAmount(amount)?.formatAsCurrency() }
        .filterNotNull()
        .asLiveData()

    val returns: LiveData<ReturnsModel> = currentAsset.combine(parsedAmountFlow) { asset, amount ->
        val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true)
        val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true)

        val monthlyEstimation = RewardEstimation(monthly.gainAmount, monthly.gainPercentage, asset.token)
        val yearlyEstimation = RewardEstimation(yearly.gainAmount, yearly.gainPercentage, asset.token)

        ReturnsModel(monthlyEstimation, yearlyEstimation)
    }.asLiveData()

    private val rewardCalculator = viewModelScope.async { rewardCalculatorFactory.create() }

    fun onAmountChanged(text: String) {
        viewModelScope.launch {
            enteredAmountFlow.emit(text)
        }
    }

    fun nextClicked() {
        launch {
            stakingSharedState.amount = parsedAmountFlow.first()

            router.openSetupStaking()
        }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: StakingAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    private suspend fun rewardCalculator(): RewardCalculator {
        return rewardCalculator.await()
    }
}
