package jp.co.soramitsu.feature_staking_impl.presentation.staking

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.wallet.formatAsToken
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val CURRENT_ICON_SIZE = 40

private val DEFAULT_AMOUNT = 10.toBigDecimal()

private const val PERIOD_MONTH = 30
private const val PERIOD_YEAR = 365

class RewardEstimation(
    amount: BigDecimal,
    fiatAmount: BigDecimal,
    percentageGain: BigDecimal,
    token: Token
) {
    val amount = amount.formatAsToken(token.type)
    val fiatAmount = fiatAmount.formatAsCurrency()
    val gain = percentageGain.formatAsChange()
}

class StakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val rewardCalculatorFactory: RewardCalculatorFactory
) : BaseViewModel() {

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    val currentAsset = interactor.getCurrentAsset()

    private val amountFlow = MutableStateFlow(DEFAULT_AMOUNT)

    val rewardCalculator: Deferred<RewardCalculator> = viewModelScope.async { rewardCalculatorFactory.create() }

    init {
        currentAsset.zip(amountFlow) { asset, amount ->

        }
        amountFlow.map {
            val monthly = rewardCalculator().calculateReturns(it, PERIOD_MONTH, true)
            val yearly = rewardCalculator().calculateReturns(it, PERIOD_YEAR, true)


        }.launchIn(viewModelScope)
    }

    fun onAmountChanged(text: String) {
        // parser & validate

        viewModelScope.launch { amountFlow.emit(BigDecimal.ONE) }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: StakingAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    suspend fun rewardCalculator() : RewardCalculator {
        return rewardCalculator.await()
    }
}