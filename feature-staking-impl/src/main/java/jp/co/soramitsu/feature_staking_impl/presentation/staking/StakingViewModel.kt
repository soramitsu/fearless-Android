package jp.co.soramitsu.feature_staking_impl.presentation.staking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.AssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.ReturnsModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.RewardEstimation
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.TokenModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

private val DEFAULT_AMOUNT = 10.toBigDecimal()

private const val PERIOD_MONTH = 30
private const val PERIOD_YEAR = 365

class StakingReturns(
    val monthly: PeriodReturns,
    val yearly: PeriodReturns
)

class StakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val rewardCalculatorFactory: RewardCalculatorFactory
) : BaseViewModel() {

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    private val _returns = MutableLiveData<ReturnsModel>()
    val returns: LiveData<ReturnsModel> = _returns

    private val currentAsset = interactor.getCurrentAsset()

    val enteredAmountFlow = MutableStateFlow(DEFAULT_AMOUNT.toString())

    private val formattedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    private val rewardCalculator = viewModelScope.async { rewardCalculatorFactory.create() }

    init {

        currentAsset.combine(formattedAmountFlow) { asset, amount ->
            val monthly = rewardCalculator().calculateReturns(amount, PERIOD_MONTH, true)
            val yearly = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true)
            val returns = StakingReturns(monthly, yearly)

            _returns.value = mapReturns(asset, returns)
        }.launchIn(viewModelScope)
    }

    private fun mapReturns(asset: Asset, stakingReturns: StakingReturns): ReturnsModel {
        val assetModel = mapAssetToAssetModel(asset)
        val monthlyFiat = asset.token.dollarRate?.multiply(stakingReturns.monthly.gainAmount)
        val yearlyFiat = asset.token.dollarRate?.multiply(stakingReturns.yearly.gainAmount)
        val monthlyEstimation = RewardEstimation(stakingReturns.monthly.gainAmount, monthlyFiat, stakingReturns.monthly.gainPercentage, asset.token)
        val yearlyEstimation = RewardEstimation(stakingReturns.yearly.gainAmount, yearlyFiat, stakingReturns.yearly.gainPercentage, asset.token)
        return ReturnsModel(assetModel, monthlyEstimation, yearlyEstimation)
    }

    fun onAmountChanged(text: String) {
        viewModelScope.launch {
            enteredAmountFlow.emit(text)
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

    private fun mapTokenToTokenModel(token: Token): TokenModel {
        return with(token) {
            TokenModel(
                type = type,
                dollarRate = dollarRate,
                recentRateChange = recentRateChange
            )
        }
    }

    private fun mapAssetToAssetModel(asset: Asset): AssetModel {
        return with(asset) {
            AssetModel(
                token = mapTokenToTokenModel(token),
                total = total,
                bonded = bonded,
                locked = locked,
                available = transferable,
                reserved = reserved,
                frozen = frozen,
                redeemable = redeemable,
                unbonding = unbonding,
                dollarAmount = dollarAmount
            )
        }
    }
}