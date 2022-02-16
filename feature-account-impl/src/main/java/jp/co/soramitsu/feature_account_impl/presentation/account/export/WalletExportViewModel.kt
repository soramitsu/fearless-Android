package jp.co.soramitsu.feature_account_impl.presentation.account.export

import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.feature_account_api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

class WalletExportViewModel(
    private val interactor: AccountDetailsInteractor,
    private val accountRouter: AccountRouter,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val metaId: Long,
    getTotalBalance: GetTotalBalanceUseCase,
    private val externalAccountActions: ExternalAccountActions.Presentation
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    val accountNameLiveData = MutableLiveData<String>()
    val accountIconLiveData = MutableLiveData<Drawable>()
    val totalBalanceLiveData = getTotalBalance(metaId).map(BigDecimal::formatAsCurrency).asLiveData()
    val amountsWithOneKeyAmountBadgeLiveData = MutableLiveData<String>()
    val amountsWithOneKeyChainNameLiveData = MutableLiveData<String>()
    val amountsWithOneKeyChainIconLiveData = MutableLiveData<String>()

    private val metaAccount = async(Dispatchers.Default) { interactor.getMetaAccount(metaId) }

    init {
        launch {
            accountNameLiveData.postValue(metaAccount().name)
            val icon = iconGenerator.createAddressIcon(metaAccount().substrateAccountId, AddressIconGenerator.SIZE_MEDIUM)
            accountIconLiveData.postValue(icon)

            val chainAccounts = interactor.getChainProjections(metaAccount())[AccountInChain.From.META_ACCOUNT]
            chainAccounts?.size?.let {
                amountsWithOneKeyAmountBadgeLiveData.postValue(resourceManager.getString(R.string.plus_others_template, it))
            }
            chainAccounts?.firstOrNull { it.chain.id == polkadotChainId }?.let {
                amountsWithOneKeyChainNameLiveData.postValue(it.chain.name)
                amountsWithOneKeyChainIconLiveData.postValue(it.chain.icon)
            }
        }
    }

    fun backClicked() {
        accountRouter.back()
    }

    fun continueClicked(from: AccountInChain.From) {
        accountRouter.openAccountsForExport(metaId, from)
    }
}
