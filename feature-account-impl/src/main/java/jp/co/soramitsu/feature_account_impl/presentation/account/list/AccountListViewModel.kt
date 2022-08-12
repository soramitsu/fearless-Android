package jp.co.soramitsu.feature_account_impl.presentation.account.list

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountChosenNavDirection {
    BACK, MAIN
}

@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    accountListingMixin: AccountListingMixin,
    private val stakingSharedState: StakingSharedState,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val accountChosenNavDirection = savedStateHandle.get<AccountChosenNavDirection>(ARG_DIRECTION)!!

    val openWalletOptionsEvent = MutableLiveData<Event<Long>>()

    val accountsFlow = accountListingMixin.accountsFlow()
        .inBackground()
        .share()

    fun optionsClicked(accountModel: LightMetaAccountUi) {
        openWalletOptionsEvent.postValue(Event(accountModel.id))
    }

    fun openWalletDetails(metaAccountId: Long) {
        accountRouter.openAccountDetails(metaAccountId)
    }

    fun openExportWallet(metaAccountId: Long) {
        accountRouter.openExportWallet(metaAccountId)
    }

    fun editClicked() {
        accountRouter.openEditAccounts()
    }

    fun selectAccountClicked(account: LightMetaAccountUi) = launch {
        accountInteractor.selectMetaAccount(account.id)
        updateStakingState()

        dispatchNavigation()
    }

    private suspend fun updateStakingState() {
        val chainAsset =
            stakingSharedState.availableToSelect().find { it.chainId.contentEquals(polkadotChainId) }
                ?: stakingSharedState.availableToSelect().first()
        stakingSharedState.update(chainAsset.chainId, chainAsset.id)
    }

    private fun dispatchNavigation() {
        when (accountChosenNavDirection) {
            AccountChosenNavDirection.BACK -> accountRouter.back()
            AccountChosenNavDirection.MAIN -> accountRouter.returnToWallet()
        }
    }

    fun backClicked() {
        accountRouter.back()
    }

    fun addAccountClicked() {
        accountRouter.openAddAccount()
    }
}
