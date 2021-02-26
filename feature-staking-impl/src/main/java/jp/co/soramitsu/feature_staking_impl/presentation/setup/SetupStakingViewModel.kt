package jp.co.soramitsu.feature_staking_impl.presentation.setup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.RewardEstimation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

private val DEFAULT_AMOUNT = 10.toBigDecimal()

private const val DESTINATION_SIZE_DP = 24

private const val PERIOD_YEAR = 365

sealed class PayoutTarget {

    object Restake : PayoutTarget()

    class Payout(val destination: AddressModel) : PayoutTarget()
}

class PayoutEstimations(
    val restake: RewardEstimation,
    val payout: RewardEstimation
)

class SetupStakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val rewardCalculatorFactory: RewardCalculatorFactory
) : BaseViewModel() {

    private val _payoutTargetLiveData = MutableLiveData<PayoutTarget>(PayoutTarget.Restake)
    val payoutTargetLiveData: LiveData<PayoutTarget> = _payoutTargetLiveData

    private val assetFlow = interactor.getCurrentAsset()
        .share()

    val assetModelsFlow = assetFlow
        .map { mapAssetToAssetModel(it) }
        .flowOn(Dispatchers.Default)

    val enteredAmountFlow = MutableStateFlow(DEFAULT_AMOUNT.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatAsCurrency()
    }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    private val rewardCalculator = viewModelScope.async { rewardCalculatorFactory.create() }
    private val accountsInCurrentNetwork = viewModelScope.async(Dispatchers.Default) {
        interactor.getAccountsInCurrentNetwork()
            .map { generateDestinationModel(it) }
    }

    val returnsLiveData = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        val restakeReturns = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true)
        val payoutReturns = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, false)

        val restakeEstimations = mapReturnsToEstimation(restakeReturns, asset.token)
        val payoutEstimations = mapReturnsToEstimation(payoutReturns, asset.token)

        PayoutEstimations(restakeEstimations, payoutEstimations)
    }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    private suspend fun rewardCalculator() = rewardCalculator.await()

    private suspend fun accountsInCurrentNetwork() = accountsInCurrentNetwork.await()

    private fun mapReturnsToEstimation(returns: PeriodReturns, token: Token): RewardEstimation {
        return RewardEstimation(returns.gainAmount, returns.gainPercentage, token)
    }

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, DESTINATION_SIZE_DP, account.name)
    }

    fun restakeClicked() {
        _payoutTargetLiveData.value = PayoutTarget.Restake
    }

    fun payoutClicked() {
        viewModelScope.launch {
            _payoutTargetLiveData.value = PayoutTarget.Payout(accountsInCurrentNetwork().first())
        }
    }
}