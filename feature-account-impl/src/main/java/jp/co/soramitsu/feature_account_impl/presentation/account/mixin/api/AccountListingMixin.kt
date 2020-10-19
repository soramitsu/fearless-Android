package jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel

class AccountListing(val groupedAccounts: List<Any>, val selectedAccount: AccountModel)

interface AccountListingMixin {
    val accountListingDisposable: CompositeDisposable

    val selectedAccountLiveData: MutableLiveData<AccountModel>

    val accountListingLiveData: LiveData<AccountListing>
}