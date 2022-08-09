package jp.co.soramitsu.featureaccountimpl.presentation.account.export

import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.featureaccountapi.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.featureaccountapi.domain.model.TotalBalance
import jp.co.soramitsu.featureaccountapi.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.featureaccountimpl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.featureaccountimpl.domain.account.details.AccountInChain
import jp.co.soramitsu.featureaccountimpl.presentation.AccountRouter
import jp.co.soramitsu.featureaccountimpl.presentation.account.model.format
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletExportViewModel @Inject constructor(
    private val interactor: AccountDetailsInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    getTotalBalance: GetTotalBalanceUseCase,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    private val metaId = savedStateHandle.get<Long>(META_ID_KEY)!!

    val accountNameLiveData = MutableLiveData<String>()
    val accountIconLiveData = MutableLiveData<Drawable>()
    val totalBalanceLiveData = getTotalBalance(metaId).map(TotalBalance::format).asLiveData()
    val amountsWithOneKeyAmountBadgeLiveData = MutableLiveData<String>()
    val amountsWithOneKeyChainNameLiveData = MutableLiveData<String>()
    val amountsWithOneKeyChainIconLiveData = MutableLiveData<String>()

    private val metaAccount = async(Dispatchers.Default) { interactor.getMetaAccount(metaId) }

    init {
        launch {
            accountNameLiveData.postValue(metaAccount().name)
            val icon = iconGenerator.createAddressIcon(metaAccount().substrateAccountId, AddressIconGenerator.SIZE_MEDIUM)
            accountIconLiveData.postValue(icon)
        }

        interactor.getChainProjectionsFlow(metaId).map {
            it[AccountInChain.From.META_ACCOUNT]?.filter { it.hasAccount }
        }.onEach { chainAccounts ->
            chainAccounts?.size?.let {
                amountsWithOneKeyAmountBadgeLiveData.postValue(resourceManager.getQuantityString(R.plurals.plus_others_template, it, it))
            }
            chainAccounts?.firstOrNull { it.chain.id == polkadotChainId }?.let {
                amountsWithOneKeyChainNameLiveData.postValue(it.chain.name)
                amountsWithOneKeyChainIconLiveData.postValue(it.chain.icon)
            }
        }.share()
    }

    fun backClicked() {
        accountRouter.back()
    }

    fun continueClicked(from: AccountInChain.From) {
        accountRouter.openAccountsForExport(metaId, from)
    }
}
