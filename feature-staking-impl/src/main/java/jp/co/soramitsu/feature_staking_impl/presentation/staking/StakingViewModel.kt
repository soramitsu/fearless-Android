package jp.co.soramitsu.feature_staking_impl.presentation.staking

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.staking.di.StakingViewStateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val CURRENT_ICON_SIZE = 40

class StakingViewModel(
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val stakingViewStateFactory: StakingViewStateFactory,
) : BaseViewModel() {

    private val currentAssetFlow = interactor.currentAssetFlow()
        .share()

    val currentStakingState = interactor.selectedAccountStakingState()
        .map { transformStakingState(it) }
        .flowOn(Dispatchers.Default)
        .share()

    val networkInfoStateLiveData = currentAssetFlow
        .map { it.token.type.networkType }
        .distinctUntilChanged()
        .withLoading(interactor::observeNetworkInfoState)
        .asLiveData()

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    private fun transformStakingState(accountStakingState: StakingState) = when (accountStakingState) {
        is StakingState.Stash.Nominator -> stakingViewStateFactory.createNominatorViewState(
            accountStakingState,
            currentAssetFlow,
            viewModelScope,
            ::showError
        )

        is StakingState.Stash.None -> stakingViewStateFactory.createWelcomeViewState(currentAssetFlow, viewModelScope)

        is StakingState.NonStash -> stakingViewStateFactory.createWelcomeViewState(currentAssetFlow, viewModelScope)

        is StakingState.Stash.Validator -> stakingViewStateFactory.createValidatorViewState()
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: StakingAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }
}
