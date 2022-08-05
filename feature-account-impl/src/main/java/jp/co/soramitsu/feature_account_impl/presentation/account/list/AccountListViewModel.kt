package jp.co.soramitsu.feature_account_impl.presentation.account.list

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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

enum class AccountChosenNavDirection {
    BACK, MAIN
}

class AccountListViewModel @AssistedInject constructor(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    @Assisted private val accountChosenNavDirection: AccountChosenNavDirection,
    accountListingMixin: AccountListingMixin,
    private val stakingSharedState: StakingSharedState,
) : BaseViewModel() {

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

    @AssistedFactory
    interface AccountListViewModelFactory {
        fun create(accountChosenNavDirection: AccountChosenNavDirection): AccountListViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: AccountListViewModelFactory,
            accountChosenNavDirection: AccountChosenNavDirection
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(accountChosenNavDirection) as T
            }
        }
    }
}
